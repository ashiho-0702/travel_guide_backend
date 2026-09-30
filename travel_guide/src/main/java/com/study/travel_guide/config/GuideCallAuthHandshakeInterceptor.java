package com.study.travel_guide.config;

import com.study.travel_guide.common.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

/**
 * 语音通话 WebSocket 握手鉴权：校验 Authorization 头（或 query 的 token），解析 userId 存入 session attributes。
 */
@Slf4j
public class GuideCallAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    public GuideCallAuthHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            log.warn("[guide-call] 握手失败：缺少 token");
            return false;
        }
        try {
            Long userId = jwtUtil.parseUserId(token);
            attributes.put("userId", userId);
            return true;
        } catch (Exception e) {
            log.warn("[guide-call] 握手失败：token 无效");
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(ServerHttpRequest request) {
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String auth = authHeaders.get(0);
            if (auth != null && auth.startsWith("Bearer ")) {
                return auth.substring(7);
            }
        }
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String kv : query.split("&")) {
                if (kv.startsWith("token=")) {
                    return kv.substring(6);
                }
            }
        }
        return null;
    }
}
