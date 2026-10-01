package com.study.travel_guide.service.guide;

import com.study.travel_guide.service.voice.BaiduStreamAsrClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 语音通话会话状态：单个 WebSocket 连接对应一个会话，串行处理每句问答，避免并发乱序。
 */
public class GuideCallSession {

    public final Long userId;
    public volatile String sessionId;
    public volatile String currentAttraction;
    public volatile Long tripId;
    public volatile String memory;
    public volatile Double currentLat;
    public volatile Double currentLng;
    public volatile BaiduStreamAsrClient asrClient;
    public final StringBuilder history = new StringBuilder();
    public final ExecutorService singleThread = Executors.newSingleThreadExecutor();
    public final AtomicLong lastFrameAt = new AtomicLong(0);
    public volatile boolean audioStarted = false;
    public volatile boolean closed = false;

    public GuideCallSession(Long userId, String sessionId) {
        this.userId = userId;
        this.sessionId = sessionId;
    }

    public void close() {
        closed = true;
        if (asrClient != null) {
            try {
                asrClient.close();
            } catch (Exception ignored) {
            }
        }
        singleThread.shutdownNow();
    }
}
