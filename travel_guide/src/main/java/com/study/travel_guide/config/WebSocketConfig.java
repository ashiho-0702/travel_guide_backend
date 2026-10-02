package com.study.travel_guide.config;

import com.study.travel_guide.common.JwtUtil;
import com.study.travel_guide.service.guide.GuideCallWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GuideCallWebSocketHandler guideCallHandler;
    private final JwtUtil jwtUtil;

    public WebSocketConfig(GuideCallWebSocketHandler guideCallHandler, JwtUtil jwtUtil) {
        this.guideCallHandler = guideCallHandler;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(guideCallHandler, "/ws/guide/call")
                .addInterceptors(new GuideCallAuthHandshakeInterceptor(jwtUtil))
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean servletServerContainerFactoryBean() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(128 * 1024);     // 128KB，容纳 base64 音频帧
        container.setMaxBinaryMessageBufferSize(128 * 1024);
        return container;
    }
}
