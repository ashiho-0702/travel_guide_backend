package com.study.travel_guide.service.guide;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.service.qwen.QwenVlService;
import com.study.travel_guide.service.voice.BaiduStreamAsrClient;
import com.study.travel_guide.service.voice.BaiduVoiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 视频通话式 AI 导游 WebSocket 端点：接收音频流（转发百度流式 ASR）+ 视频帧（qwen-vl 识图），
 * 识别到整句后调 ConversationService 生成回答 + TTS 回传。
 */
@Slf4j
@Component
public class GuideCallWebSocketHandler implements WebSocketHandler {

    private final BaiduVoiceService baiduVoiceService;
    private final ConversationService conversationService;
    private final QwenVlService qwenVlService;
    private final JsonMapper jsonMapper;
    private final ExecutorService guideCallExecutor;

    private final Map<String, GuideCallSession> sessions = new ConcurrentHashMap<>();

    public GuideCallWebSocketHandler(BaiduVoiceService baiduVoiceService,
                                     ConversationService conversationService,
                                     QwenVlService qwenVlService,
                                     JsonMapper jsonMapper,
                                     @Qualifier("guideCallExecutor") ExecutorService guideCallExecutor) {
        this.baiduVoiceService = baiduVoiceService;
        this.conversationService = conversationService;
        this.qwenVlService = qwenVlService;
        this.jsonMapper = jsonMapper;
        this.guideCallExecutor = guideCallExecutor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        GuideCallSession call = new GuideCallSession(userId, null);
        BaiduStreamAsrClient asr = new BaiduStreamAsrClient(
                baiduVoiceService.getAccessToken(),
                baiduVoiceService.getApiKey(),
                baiduVoiceService.getAppId(),
                jsonMapper,
                new AsrListener(session, call));
        call.asrClient = asr;
        sessions.put(session.getId(), call);
        asr.start();
        send(session, Map.of("type", "ready"));
        log.info("[guide-call] 通话建立: userId={}, wsId={}", userId, session.getId());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        GuideCallSession call = sessions.get(session.getId());
        if (call == null) {
            return;
        }
        Object payload = message.getPayload();
        if (payload instanceof String text) {
            handleText(session, call, text);
        }
    }

    private void handleText(WebSocketSession session, GuideCallSession call, String text) {
        try {
            JsonNode msg = jsonMapper.readTree(text);
            String type = msg.path("type").asText();
            switch (type) {
                case "start" -> {
                    String sid = msg.path("sessionId").asText();
                    if (sid != null && !sid.isBlank()) {
                        call.sessionId = sid;
                    }
                    send(session, Map.of("type", "ready", "sessionId", call.sessionId));
                }
                case "audio" -> {
                    String data = msg.path("data").asText();
                    if (data != null && !data.isBlank()) {
                        byte[] pcm = Base64.getDecoder().decode(data);
                        call.asrClient.sendAudio(pcm);
                    }
                }
                case "frame" -> handleFrame(session, call, msg.path("data").asText());
                case "stop" -> closeSession(session);
                default -> log.debug("[guide-call] 未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.warn("[guide-call] 消息处理失败: {}", e.getMessage());
        }
    }

    private void handleFrame(WebSocketSession session, GuideCallSession call, String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = call.lastFrameAt.get();
        if (now - last < 1000) {
            return; // 限流 1fps
        }
        if (!call.lastFrameAt.compareAndSet(last, now)) {
            return;
        }
        guideCallExecutor.execute(() -> {
            try {
                String name = qwenVlService.identifyAttraction(imageBase64);
                if (name != null && !name.isBlank()) {
                    call.currentAttraction = name;
                    send(session, Map.of("type", "attraction", "name", name));
                }
            } catch (Exception e) {
                log.warn("[guide-call] 识图失败: {}", e.getMessage());
            }
        });
    }

    private void closeSession(WebSocketSession session) {
        GuideCallSession call = sessions.remove(session.getId());
        if (call != null) {
            call.close();
            log.info("[guide-call] 通话结束: wsId={}", session.getId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[guide-call] 传输错误: {}", exception.getMessage());
        closeSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus closeStatus) {
        closeSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void send(WebSocketSession session, Map<String, Object> data) {
        try {
            String json = jsonMapper.writeValueAsString(data);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            log.warn("[guide-call] 发送消息失败: {}", e.getMessage());
        }
    }

    private class AsrListener implements BaiduStreamAsrClient.Listener {
        private final WebSocketSession session;
        private final GuideCallSession call;

        AsrListener(WebSocketSession session, GuideCallSession call) {
            this.session = session;
            this.call = call;
        }

        @Override
        public void onPartial(String text) {
            send(session, Map.of("type", "asr_partial", "text", text));
        }

        @Override
        public void onFinal(String text) {
            if (call.closed) {
                return;
            }
            send(session, Map.of("type", "asr_final", "text", text));
            // 串行队列里做 chat + tts，避免并发乱序
            call.singleThread.execute(() -> {
                try {
                    Map<String, Object> resp = conversationService.chat(call.userId, call.sessionId, text, call.currentAttraction);
                    String answer = resp.get("answer") == null ? "" : resp.get("answer").toString();
                    if (resp.get("sessionId") != null) {
                        call.sessionId = resp.get("sessionId").toString();
                    }
                    if (answer.isBlank()) {
                        return;
                    }
                    String tts = baiduVoiceService.tts(answer);
                    send(session, Map.of("type", "tts", "data", tts, "text", answer));
                } catch (Exception e) {
                    log.warn("[guide-call] 生成回答失败: {}", e.getMessage());
                    send(session, Map.of("type", "error", "message", e.getMessage()));
                }
            });
        }

        @Override
        public void onError(String message) {
            send(session, Map.of("type", "error", "message", message));
        }
    }
}
