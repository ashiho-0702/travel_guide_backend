package com.study.travel_guide.service.voice;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.zip.GZIPInputStream;

/**
 * 百度实时语音识别（WebSocket 流式 ASR）客户端。
 * 协议：连 wss://ws-api.baidu.com/v2/recognize?token=...，
 * 首帧发 START（Text JSON）→ 持续发 PCM 音频（Binary）→ 最后发 FINISH（Text JSON），
 * 服务端实时返回识别结果（Text/Binary，可能 gzip）。
 */
@Slf4j
public class BaiduStreamAsrClient {

    public interface Listener {
        void onPartial(String text);

        void onFinal(String text);

        void onError(String message);
    }

    private final Listener listener;
    private final JsonMapper jsonMapper;
    private final String token;
    private final String appkey;
    private final String appid;

    private WebSocket webSocket;
    private volatile boolean closed = false;

    public BaiduStreamAsrClient(String token, String appkey, String appid,
                                JsonMapper jsonMapper, Listener listener) {
        this.token = token;
        this.appkey = appkey;
        this.appid = appid;
        this.jsonMapper = jsonMapper;
        this.listener = listener;
    }

    public void start() {
        try {
            String url = "wss://ws-api.baidu.com/v2/recognize?dev_pid=1537&format=pcm&rate=16000&token=" + token;
            HttpClient client = HttpClient.newHttpClient();
            this.webSocket = client.newWebSocketBuilder()
                    .buildAsync(URI.create(url), new WsListener())
                    .join();
        } catch (Exception e) {
            log.error("[asr] 连接百度流式 ASR 失败: {}", e.getMessage());
            listener.onError(e.getMessage());
        }
    }

    public void sendAudio(byte[] pcm) {
        if (webSocket != null && !closed && pcm != null && pcm.length > 0) {
            try {
                webSocket.sendBinary(ByteBuffer.wrap(pcm), true);
            } catch (Exception e) {
                log.warn("[asr] 发送音频帧失败: {}", e.getMessage());
            }
        }
    }

    public void finish() {
        if (webSocket != null && !closed) {
            try {
                webSocket.sendText("{\"type\":\"FINISH\"}", true);
            } catch (Exception e) {
                log.warn("[asr] 发送 FINISH 失败: {}", e.getMessage());
            }
        }
    }

    public void close() {
        closed = true;
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {
            }
        }
    }

    private String buildStartFrame() {
        return "{\"type\":\"START\",\"data\":{\"appid\":" + (appid == null || appid.isBlank() ? "0" : appid)
                + ",\"appkey\":\"" + (appkey == null ? "" : appkey)
                + "\",\"dev_pid\":1537,\"cuid\":\"travel_guide\",\"format\":\"pcm\",\"sample\":16000}}";
    }

    private void handleResult(String json) {
        try {
            JsonNode root = jsonMapper.readTree(json);
            String type = root.path("type").asText();
            String text = root.path("result").asText();
            if (text == null || text.isBlank()) {
                JsonNode r = root.path("result");
                if (r.isObject()) {
                    text = r.path("result").asText();
                }
            }
            if (text == null || text.isBlank()) {
                return;
            }
            if (type.contains("FIN") || type.contains("fin") || type.contains("Final")) {
                listener.onFinal(text);
            } else {
                listener.onPartial(text);
            }
        } catch (Exception e) {
            log.warn("[asr] 解析识别结果失败: {}", e.getMessage());
        }
    }

    private String decompress(byte[] data) {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = gis.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private class WsListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
            ws.sendText(buildStartFrame(), true);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            ws.request(1);
            handleResult(data.toString());
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            ws.request(1);
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            handleResult(decompress(bytes));
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("[asr] 百度流式 ASR 连接错误: {}", error.getMessage());
            listener.onError(error.getMessage());
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.info("[asr] 百度流式 ASR 连接关闭: {} {}", statusCode, reason);
            return null;
        }
    }
}
