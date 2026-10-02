package com.study.travel_guide.service.guide;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.study.travel_guide.service.agent.AgentService;
import com.study.travel_guide.service.qwen.QwenVlService;
import com.study.travel_guide.service.voice.BaiduStreamAsrClient;
import com.study.travel_guide.service.voice.BaiduVoiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 视频通话式 AI 搭子 WebSocket 端点：接收音频流（转发百度流式 ASR）+ 视频帧（qwen-vl 识图），
 * 识别到整句后走 Agent（搭子人设 + 攻略记忆摘要 + 附近/天气工具）生成回答并 TTS 回传。
 */
@Slf4j
@Component
public class GuideCallWebSocketHandler implements WebSocketHandler {

    private final BaiduVoiceService baiduVoiceService;
    private final QwenVlService qwenVlService;
    private final AgentService agentService;
    private final BuddyMemoryService buddyMemoryService;
    private final JsonMapper jsonMapper;
    private final StringRedisTemplate redisTemplate;
    private final BuddyReminderService buddyReminderService;
    private final ExecutorService guideCallExecutor;

    private final Map<String, GuideCallSession> sessions = new ConcurrentHashMap<>();

    public GuideCallWebSocketHandler(BaiduVoiceService baiduVoiceService,
                                     QwenVlService qwenVlService,
                                     AgentService agentService,
                                     BuddyMemoryService buddyMemoryService,
                                     JsonMapper jsonMapper,
                                     StringRedisTemplate redisTemplate,
                                     BuddyReminderService buddyReminderService,
                                     @Qualifier("guideCallExecutor") ExecutorService guideCallExecutor) {
        this.baiduVoiceService = baiduVoiceService;
        this.qwenVlService = qwenVlService;
        this.agentService = agentService;
        this.buddyMemoryService = buddyMemoryService;
        this.jsonMapper = jsonMapper;
        this.redisTemplate = redisTemplate;
        this.buddyReminderService = buddyReminderService;
        this.guideCallExecutor = guideCallExecutor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        GuideCallSession call = new GuideCallSession(userId, null);
        BaiduStreamAsrClient asr = new BaiduStreamAsrClient(
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
                    long tripId = msg.path("tripId").asLong();
                    if (tripId > 0) {
                        call.tripId = tripId;
                        try {
                            call.memory = buddyMemoryService.buildMemory(call.userId, tripId);
                        } catch (Exception e) {
                            log.warn("[guide-call] 生成搭子记忆失败: {}", e.getMessage());
                        }
                        String hist = readHistory(call.userId, tripId);
                        if (hist != null && !hist.isBlank()) {
                            call.history.append(hist);
                            log.info("[guide-call] 加载历史对话: {} 字符", hist.length());
                        }
                        startReminders(session, call, tripId);
                    }
                    if (msg.has("lat") && msg.has("lng")) {
                        call.currentLat = msg.path("lat").asDouble();
                        call.currentLng = msg.path("lng").asDouble();
                    }
                    send(session, Map.of("type", "ready", "sessionId", call.sessionId == null ? "" : call.sessionId));
                }
                case "audio" -> {
                    String data = msg.path("data").asText();
                    if (data != null && !data.isBlank()) {
                        byte[] pcm = Base64.getDecoder().decode(data);
                        if (!call.audioStarted) {
                            call.audioStarted = true;
                            log.info("[guide-call] 首次收到音频帧，大小={} 字节", pcm.length);
                        }
                        ensureAsr(session, call);
                        call.asrClient.sendAudio(pcm);
                    }
                }
                case "frame" -> handleFrame(session, call, msg.path("data").asText());
                case "location" -> {
                    call.currentLat = msg.path("lat").asDouble();
                    call.currentLng = msg.path("lng").asDouble();
                }
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
        if (now - last < 10000) {
            return;
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

    private String buildBuddyPrompt(GuideCallSession call) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是用户的旅行搭子，一个幽默风趣、热情开朗的伙伴，像好朋友一样陪着玩。")
                .append("说话生动有温度，会开玩笑、用点俏皮话，但别油腻、别硬凑梗，自然就好。")
                .append("你能解答行程问题（今天去哪、景点介绍、门票、时长）、记账问题（花了多少、分类），也能闲聊打趣。")
                .append("需要实时信息时调用工具：推荐附近好玩的（search_nearby）、查天气（get_weather）、讲解景点（narrate）。")
                .append("用户想听讲解时，先用 search_nearby（用户当前定位）识别景点，再调 narrate 生成讲解词。")
                .append("如果不知道用户当前位置，不要调 search_nearby，直接告诉用户你无法定位。")
                .append("用户问攻略、景点、美食、路线等旅行建议时，先调 search_kb 检索本地攻略知识库，命中就用库里的内容，没命中再靠自己的知识补充。")
                .append("你只读，不修改行程或记账。回答口语化、简洁（60-100 字，适合语音播报），幽默但不影响信息准确。\n\n");
        if (call.currentAttraction != null && !call.currentAttraction.isBlank()) {
            sb.append("用户当前识别的景点：").append(call.currentAttraction).append("\n");
        }
        if (call.currentLat != null && call.currentLng != null) {
            sb.append("用户当前大概位置：纬度 ").append(call.currentLat)
                    .append("，经度 ").append(call.currentLng).append("\n");
        }
        if (call.memory != null && !call.memory.isBlank()) {
            sb.append("这份攻略的记忆摘要：\n").append(call.memory).append("\n");
        }
        if (call.history.length() > 0) {
            sb.append("之前的对话：\n").append(call.history).append("\n");
        }
        return sb.toString();
    }

    private void appendHistory(GuideCallSession call, String question, String answer) {
        synchronized (call.history) {
            call.history.append("用户：").append(question).append("\n");
            call.history.append("搭子：").append(answer).append("\n");
            if (call.history.length() > 2000) {
                String s = call.history.toString();
                call.history.setLength(0);
                call.history.append(s.substring(s.length() - 2000));
            }
        }
    }

    private void startReminders(WebSocketSession session, GuideCallSession call, long tripId) {
        call.singleThread.execute(() -> {
            try {
                String reminder = buddyReminderService.checkBookingReminder(call.userId, tripId);
                if (reminder != null && !reminder.isBlank()) {
                    sendReminderTts(session, call, reminder);
                }
            } catch (Exception e) {
                log.warn("[remind] 预约提醒失败: {}", e.getMessage());
            }
        });
        call.reminderTimer = Executors.newSingleThreadScheduledExecutor();
        call.reminderTimer.scheduleAtFixedRate(() -> {
            if (call.closed) {
                return;
            }
            try {
                String reminder = buddyReminderService.checkWeatherReminder(call.userId, tripId);
                if (reminder != null && !reminder.isBlank()) {
                    sendReminderTts(session, call, reminder);
                }
            } catch (Exception e) {
                log.warn("[remind] 天气提醒失败: {}", e.getMessage());
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    private void sendReminderTts(WebSocketSession session, GuideCallSession call, String reminder) {
        try {
            String tts = baiduVoiceService.tts(reminder);
            send(session, Map.of("type", "tts", "data", tts, "text", reminder));
        } catch (Exception e) {
            log.warn("[remind] 提醒 TTS 失败: {}", e.getMessage());
        }
    }

    private void ensureAsr(WebSocketSession session, GuideCallSession call) {
        if (call.asrClient == null || call.asrClient.isClosed()) {
            BaiduStreamAsrClient asr = new BaiduStreamAsrClient(
                    baiduVoiceService.getApiKey(),
                    baiduVoiceService.getAppId(),
                    jsonMapper,
                    new AsrListener(session, call));
            call.asrClient = asr;
            asr.start();
            log.info("[guide-call] 重新建立 ASR 会话");
        }
    }

    private void closeSession(WebSocketSession session) {
        GuideCallSession call = sessions.remove(session.getId());
        if (call != null) {
            saveHistory(call);
            call.close();
            log.info("[guide-call] 通话结束: wsId={}", session.getId());
        }
    }

    private String readHistory(Long userId, Long tripId) {
        try {
            return redisTemplate.opsForValue().get("guide:call:" + userId + ":" + tripId);
        } catch (Exception e) {
            log.warn("[guide-call] 读取历史失败: {}", e.getMessage());
            return null;
        }
    }

    private void saveHistory(GuideCallSession call) {
        if (call.tripId == null || call.history.length() == 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set("guide:call:" + call.userId + ":" + call.tripId,
                    call.history.toString(), Duration.ofDays(7));
        } catch (Exception e) {
            log.warn("[guide-call] 保存历史失败: {}", e.getMessage());
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
            log.info("[guide-call] 识别到整句 FINAL: {}", text);
            send(session, Map.of("type", "asr_final", "text", text));
            call.singleThread.execute(() -> {
                try {
                    log.info("[guide-call] 开始生成回答: {}", text);
                    String answer = agentService.run(buildBuddyPrompt(call), text, 3);
                    if (answer == null || answer.isBlank()) {
                        log.warn("[guide-call] 生成回答为空");
                        return;
                    }
                    log.info("[guide-call] 生成回答: {}", answer);
                    appendHistory(call, text, answer);
                    String tts = baiduVoiceService.tts(answer);
                    send(session, Map.of("type", "tts", "data", tts, "text", answer));
                    // 一句结束，关闭 ASR 会话，下一句重新建立，避免静音超时
                    if (call.asrClient != null) {
                        call.asrClient.finish();
                        call.asrClient.close();
                    }
                } catch (Exception e) {
                    log.warn("[guide-call] 生成回答失败: {}", e.getMessage());
                    send(session, Map.of("type", "error", "message", e.getMessage()));
                }
            });
        }

        @Override
        public void onError(String message) {
            log.warn("[guide-call] ASR 错误: {}", message);
            send(session, Map.of("type", "error", "message", message));
        }
    }
}
