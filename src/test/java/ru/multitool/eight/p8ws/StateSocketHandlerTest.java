package ru.multitool.eight.p8ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Обработчик WebSocket проверяется на моке сессии: жизненный цикл, валидация
 * команд, лимит размера сообщения и освобождение ресурсов.
 */
class StateSocketHandlerTest {

    private static final int MAX_PAYLOAD = 200;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> frames = new ArrayList<>();

    private StateSocketHandler handler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() throws Exception {
        this.handler = new StateSocketHandler(this.objectMapper, MAX_PAYLOAD, 5000L);
        this.frames.clear();

        this.session = mock(WebSocketSession.class);
        when(this.session.getId()).thenReturn("session-1");
        when(this.session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            TextMessage message = invocation.getArgument(0);
            this.frames.add(message.getPayload());
            return null;
        }).when(this.session).sendMessage(any());

        this.handler.afterConnectionEstablished(this.session);
    }

    private JsonNode lastFrame() throws Exception {
        assertThat(this.frames).isNotEmpty();
        return this.objectMapper.readTree(this.frames.get(this.frames.size() - 1));
    }

    @Test
    @DisplayName("при открытии приходит welcome с состоянием и лимитом")
    void welcomeFrameIsSent() throws Exception {
        assertThat(this.frames).hasSize(1);

        JsonNode frame = lastFrame();
        assertThat(frame.get("type").asText()).isEqualTo("welcome");
        assertThat(frame.get("state").asText()).isEqualTo("NEW");
        assertThat(frame.get("maxPayloadBytes").asInt()).isEqualTo(MAX_PAYLOAD);
        assertThat(this.handler.activeSessions()).isEqualTo(1);
    }

    @Test
    @DisplayName("допустимая команда меняет состояние и присылает историю")
    void allowedCommandMovesState() throws Exception {
        this.handler.handleTextMessage(this.session, new TextMessage("{\"command\":\"IN_PROGRESS\",\"note\":\"взято в работу\"}"));

        JsonNode frame = lastFrame();
        assertThat(frame.get("type").asText()).isEqualTo("state");
        assertThat(frame.get("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(frame.get("history").toString()).contains("взято в работу");
    }

    @Test
    @DisplayName("недопустимый переход возвращает ошибку и не меняет состояние")
    void forbiddenCommandIsRejected() throws Exception {
        this.handler.handleTextMessage(this.session, new TextMessage("{\"command\":\"DONE\"}"));

        JsonNode frame = lastFrame();
        assertThat(frame.get("type").asText()).isEqualTo("error");
        assertThat(frame.get("code").asText()).isEqualTo("INVALID_TRANSITION");

        this.handler.handleTextMessage(this.session, new TextMessage("{\"command\":\"IN_PROGRESS\"}"));
        assertThat(lastFrame().get("state").asText()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("слишком большое сообщение отклоняется по лимиту")
    void oversizedMessageIsRejected() throws Exception {
        String big = "{\"command\":\"IN_PROGRESS\",\"note\":\"" + "а".repeat(MAX_PAYLOAD * 2) + "\"}";

        this.handler.handleTextMessage(this.session, new TextMessage(big));

        JsonNode frame = lastFrame();
        assertThat(frame.get("type").asText()).isEqualTo("error");
        assertThat(frame.get("code").asText()).isEqualTo("MESSAGE_TOO_LARGE");
    }

    @Test
    @DisplayName("неизвестная команда и битый JSON не ломают обработчик")
    void malformedFramesAreRejected() throws Exception {
        this.handler.handleTextMessage(this.session, new TextMessage("не json"));
        assertThat(lastFrame().get("code").asText()).isEqualTo("MALFORMED_FRAME");

        this.handler.handleTextMessage(this.session, new TextMessage("{\"command\":\"FLY\"}"));
        assertThat(lastFrame().get("code").asText()).isEqualTo("UNKNOWN_COMMAND");
    }

    @Test
    @DisplayName("закрытие соединения освобождает сессию")
    void closedSessionIsRemoved() {
        assertThat(this.handler.activeSessions()).isEqualTo(1);

        this.handler.afterConnectionClosed(this.session, CloseStatus.NORMAL);

        assertThat(this.handler.activeSessions()).isZero();
    }
}
