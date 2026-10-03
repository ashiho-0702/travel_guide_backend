package com.study.travel_guide.service.voice;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import lombok.extern.slf4j.Slf4j;
import okio.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * 百度实时语音识别（WebSocket 流式 ASR）客户端（OkHttp 实现，避开 JDK HttpClient 握手兼容问题）。
 * 协议：连 wss://vop.baidu.com/realtime_asr?sn=...（sn 自定义 UUID），
 * 首帧发 START（Text JSON，带 appid/appkey）→ 持续发 PCM 音频（Binary）→ 最后发 FINISH（Text JSON），
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
    private final String appkey;
    private final String appid;

    private WebSocket webSocket;
    private volatile boolean closed = false;
    private volatile boolean opened = false;
    private final List<byte[]> pendingAudio = new ArrayList<>();

    public BaiduStreamAsrClient(String appkey, String appid,
                                JsonMapper jsonMapper, Listener listener) {
        this.appkey = appkey;
        this.appid = appid;
        this.jsonMapper = jsonMapper;
        this.listener = listener;
    }

    public void start() {
        try {
            String url = "wss://vop.baidu.com/realtime_asr?sn=" + UUID.randomUUID();
            OkHttpClient client = new OkHttpClient.Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .build();
            Request request = new Request.Builder().url(url).build();
            this.webSocket = client.newWebSocket(request, new WsListener());
        } catch (Exception e) {
            log.error("[asr] 连接百度流式 ASR 失败: {}", e.getMessage());
            listener.onError(e.getMessage());
        }
    }

    public void sendAudio(byte[] pcm) {
        if (closed || pcm == null || pcm.length == 0) {
            return;
        }
        synchronized (pendingAudio) {
            if (!opened) {
                // START 帧还没发出去，先缓存，避免音频帧排在 START 前面
                pendingAudio.add(pcm);
                return;
            }
        }
        sendBinary(pcm);
    }

    private void sendBinary(byte[] pcm) {
        if (webSocket != null) {
            try {
                webSocket.send(ByteString.of(pcm));
            } catch (Exception e) {
                log.warn("[asr] 发送音频帧失败: {}", e.getMessage());
            }
        }
    }

    public void finish() {
        if (webSocket != null && !closed) {
            try {
                webSocket.send("{\"type\":\"FINISH\"}");
            } catch (Exception e) {
                log.warn("[asr] 发送 FINISH 失败: {}", e.getMessage());
            }
        }
    }

    public void close() {
        closed = true;
        if (webSocket != null) {
            try {
                webSocket.close(1000, "bye");
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isClosed() {
        return closed;
    }

    private String buildStartFrame() {
        return "{\"type\":\"START\",\"data\":{\"appid\":" + (appid == null || appid.isBlank() ? "0" : appid)
                + ",\"appkey\":\"" + (appkey == null ? "" : appkey)
                + "\",\"dev_pid\":15372,\"cuid\":\"travel_guide\",\"format\":\"pcm\",\"sample\":16000}}";
    }

    private void handleResult(String json) {
        try {
            JsonNode root = jsonMapper.readTree(json);
            int errNo = root.path("err_no").asInt(0);
            if (errNo != 0) {
                log.warn("[asr] 百度返回错误码 {}: {}", errNo, root.path("err_msg").asText());
                close();
                return;
            }
            String type = root.path("type").asText();
            String text = extractText(root.path("result"));
            if (text == null || text.isBlank()) {
                log.debug("[asr] 识别结果无文本（type={}）: {}", type, json);
                return;
            }
            if (type.contains("FIN") || type.contains("fin") || type.contains("Final")) {
                log.info("[asr] 识别最终结果 FINAL: {}", text);
                listener.onFinal(text);
            } else {
                log.debug("[asr] 识别中间结果 partial: {}", text);
                listener.onPartial(text);
            }
        } catch (Exception e) {
            log.warn("[asr] 解析识别结果失败: {}", e.getMessage());
        }
    }

    private String extractText(JsonNode result) {
        if (result == null || result.isMissingNode()) {
            return null;
        }
        if (result.isArray()) {
            return result.size() > 0 ? result.get(0).asText() : null;
        }
        return result.asText();
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

    private class WsListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket ws, Response response) {
            if (closed) {
                log.info("[asr] 连接在打开前已关闭，忽略 onOpen");
                return;
            }
            log.info("[asr] 百度 WebSocket 已连接，发送 START 帧");
            synchronized (pendingAudio) {
                ws.send(buildStartFrame());
                opened = true;
                for (byte[] pcm : pendingAudio) {
                    sendBinary(pcm);
                }
                pendingAudio.clear();
            }
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            handleResult(text);
        }

        @Override
        public void onMessage(WebSocket ws, ByteString bytes) {
            String json = decompress(bytes.toByteArray());
            handleResult(json);
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            closed = true;
            log.warn("[asr] 百度流式 ASR 连接错误: {}", t == null ? "unknown" : t.getMessage());
            listener.onError(t == null ? "unknown" : t.getMessage());
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            closed = true;
            log.info("[asr] 百度流式 ASR 连接关闭: {} {}", code, reason);
        }
    }
}
