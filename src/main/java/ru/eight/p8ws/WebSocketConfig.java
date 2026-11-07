package ru.eight.p8ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Проект 8. WebSocket: регистрация обработчика и ограничения на размеры сообщений и буферов.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final StateSocketHandler handler;
    private final int maxTextMessageBytes;
    private final int bufferSizeBytes;
    private final long sendTimeLimitMs;

    public WebSocketConfig(StateSocketHandler handler,
                           @Value("${eight.websocket.max-text-message-bytes:8192}") int maxTextMessageBytes,
                           @Value("${eight.websocket.buffer-size-bytes:16384}") int bufferSizeBytes,
                           @Value("${eight.websocket.send-time-limit-ms:10000}") long sendTimeLimitMs) {
        this.handler = handler;
        this.maxTextMessageBytes = maxTextMessageBytes;
        this.bufferSizeBytes = bufferSizeBytes;
        this.sendTimeLimitMs = sendTimeLimitMs;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(this.handler, "/ws/state").setAllowedOriginPatterns("*");
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(this.maxTextMessageBytes);
        container.setMaxBinaryMessageBufferSize(this.maxTextMessageBytes);
        container.setMaxSessionIdleTimeout(120_000L);
        container.setAsyncSendTimeout(this.sendTimeLimitMs);
        return container;
    }
}
