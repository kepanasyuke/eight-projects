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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Матрица протокола WebSocket: для каждой пары «история команд → новая команда»
 * проверяется тип кадра и итоговое состояние машины.
 */
class StateCommandMatrixTest {

    private static final int MAX_PAYLOAD = 160;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> frames = new ArrayList<>();

    private StateSocketHandler handler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() throws Exception {
        this.handler = new StateSocketHandler(this.objectMapper, MAX_PAYLOAD, 5000L);
        this.frames.clear();
        this.session = mock(WebSocketSession.class);
        when(this.session.getId()).thenReturn("matrix-session");
        when(this.session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            this.frames.add(((TextMessage) invocation.getArgument(0)).getPayload());
            return null;
        }).when(this.session).sendMessage(any());
        this.handler.afterConnectionEstablished(this.session);
    }

    private void send(String command) throws Exception {
        this.handler.handleTextMessage(this.session, new TextMessage("{\"command\":\"" + command + "\"}"));
    }

    private JsonNode last() throws Exception {
        return this.objectMapper.readTree(this.frames.get(this.frames.size() - 1));
    }

    static Stream<Arguments> transitions() {
        return Stream.of(
                Arguments.of(List.of(), "IN_PROGRESS", "state", "IN_PROGRESS"),
                Arguments.of(List.of(), "CANCELLED", "state", "CANCELLED"),
                Arguments.of(List.of(), "BLOCKED", "error", null),
                Arguments.of(List.of(), "DONE", "error", null),
                Arguments.of(List.of(), "NEW", "error", null),
                Arguments.of(List.of(), "in_progress", "state", "IN_PROGRESS"),
                Arguments.of(List.of("IN_PROGRESS"), "BLOCKED", "state", "BLOCKED"),
                Arguments.of(List.of("IN_PROGRESS"), "DONE", "state", "DONE"),
                Arguments.of(List.of("IN_PROGRESS"), "CANCELLED", "state", "CANCELLED"),
                Arguments.of(List.of("IN_PROGRESS"), "NEW", "error", null),
                Arguments.of(List.of("IN_PROGRESS"), "IN_PROGRESS", "error", null),
                Arguments.of(List.of("IN_PROGRESS", "BLOCKED"), "IN_PROGRESS", "state", "IN_PROGRESS"),
                Arguments.of(List.of("IN_PROGRESS", "BLOCKED"), "CANCELLED", "state", "CANCELLED"),
                Arguments.of(List.of("IN_PROGRESS", "BLOCKED"), "DONE", "error", null),
                Arguments.of(List.of("IN_PROGRESS", "DONE"), "IN_PROGRESS", "error", null),
                Arguments.of(List.of("IN_PROGRESS", "DONE"), "CANCELLED", "error", null),
                Arguments.of(List.of("CANCELLED"), "IN_PROGRESS", "error", null),
                Arguments.of(List.of("CANCELLED"), "CANCELLED", "error", null)
        );
    }

    @ParameterizedTest(name = "{0} + {1} → {2}")
    @MethodSource("transitions")
    @DisplayName("команда из истории даёт ожидаемый кадр")
    void commandMatrix(List<String> history, String command, String frameType, String expectedState) throws Exception {
        for (String step : history) {
            this.send(step);
        }

        int before = this.frames.size();
        this.send(command);

        assertThat(this.frames).hasSize(before + 1);
        JsonNode frame = last();
        assertThat(frame.get("type").asText()).isEqualTo(frameType);
        if (expectedState != null) {
            assertThat(frame.get("state").asText()).isEqualTo(expectedState);
        } else {
            assertThat(frame.get("code").asText()).isEqualTo("INVALID_TRANSITION");
            assertThat(frame.get("message").asText()).contains("недопустим");
        }
    }

    @Test
    @DisplayName("welcome содержит идентификатор сессии, состояние и лимит")
    void welcomeFrameDescribesSession() throws Exception {
        JsonNode frame = this.objectMapper.readTree(this.frames.get(0));

        assertThat(frame.get("type").asText()).isEqualTo("welcome");
        assertThat(frame.get("sessionId").asText()).isEqualTo("matrix-session");
        assertThat(frame.get("state").asText()).isEqualTo("NEW");
        assertThat(frame.get("allowed").toString()).contains("IN_PROGRESS").contains("CANCELLED");
        assertThat(frame.get("maxPayloadBytes").asInt()).isEqualTo(MAX_PAYLOAD);
    }

    @Test
    @DisplayName("история пополняется каждой допустимой командой")
    void historyGrows() throws Exception {
        this.handler.handleTextMessage(this.session,
                new TextMessage("{\"command\":\"IN_PROGRESS\",\"note\":\"взял в работу\"}"));

        JsonNode frame = last();
        assertThat(frame.get("history").toString()).contains("NEW");
        assertThat(frame.get("history").toString()).contains("взял в работу");
        assertThat(frame.get("allowed").toString()).contains("DONE");
    }

    static Stream<Arguments> brokenFrames() {
        return Stream.of(
                Arguments.of("не json", "MALFORMED_FRAME"),
                Arguments.of("{\"note\":\"без команды\"}", "MALFORMED_FRAME"),
                Arguments.of("{\"command\":null}", "MALFORMED_FRAME"),
                Arguments.of("{\"command\":\"FLY\"}", "UNKNOWN_COMMAND"),
                Arguments.of("{\"command\":\"\"}", "UNKNOWN_COMMAND"),
                Arguments.of("{\"command\":\"donee\"}", "UNKNOWN_COMMAND")
        );
    }

    @ParameterizedTest(name = "битый кадр: {0}")
    @MethodSource("brokenFrames")
    @DisplayName("некорректные кадры не меняют состояние и возвращают код ошибки")
    void brokenFramesAreRejected(String payload, String code) throws Exception {
        this.handler.handleTextMessage(this.session, new TextMessage(payload));

        JsonNode frame = last();
        assertThat(frame.get("type").asText()).isEqualTo("error");
        assertThat(frame.get("code").asText()).isEqualTo(code);

        this.send("IN_PROGRESS");
        assertThat(last().get("state").asText()).isEqualTo("IN_PROGRESS");
    }

    static Stream<Arguments> sizes() {
        return Stream.of(
                Arguments.of(MAX_PAYLOAD - 1, false),
                Arguments.of(MAX_PAYLOAD, false),
                Arguments.of(MAX_PAYLOAD + 1, true),
                Arguments.of(MAX_PAYLOAD * 3, true)
        );
    }

    @ParameterizedTest(name = "размер {0} байт → превышение: {1}")
    @MethodSource("sizes")
    @DisplayName("лимит размера сообщения проверяется по фактической длине")
    void sizeLimit(int size, boolean tooLarge) throws Exception {
        String prefix = "{\"command\":\"IN_PROGRESS\",\"note\":\"";
        String suffix = "\"}";
        String payload = prefix + "a".repeat(Math.max(0, size - prefix.length() - suffix.length())) + suffix;

        assertThat(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(size);
        this.handler.handleTextMessage(this.session, new TextMessage(payload));

        JsonNode frame = last();
        if (tooLarge) {
            assertThat(frame.get("code").asText()).isEqualTo("MESSAGE_TOO_LARGE");
        } else {
            assertThat(frame.get("type").asText()).isEqualTo("state");
        }
    }

    @Test
    @DisplayName("сообщение от незарегистрированной сессии отклоняется")
    void unknownSessionIsRejected() throws Exception {
        WebSocketSession stranger = mock(WebSocketSession.class);
        when(stranger.getId()).thenReturn("stranger");
        when(stranger.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            this.frames.add(((TextMessage) invocation.getArgument(0)).getPayload());
            return null;
        }).when(stranger).sendMessage(any());

        this.handler.handleTextMessage(stranger, new TextMessage("{\"command\":\"IN_PROGRESS\"}"));

        assertThat(last().get("code").asText()).isEqualTo("NOT_REGISTERED");
    }

    @Test
    @DisplayName("сессии не разделяют состояние")
    void sessionsAreIsolated() throws Exception {
        WebSocketSession second = mock(WebSocketSession.class);
        when(second.getId()).thenReturn("second");
        when(second.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            this.frames.add(((TextMessage) invocation.getArgument(0)).getPayload());
            return null;
        }).when(second).sendMessage(any());

        this.send("IN_PROGRESS");
        this.handler.afterConnectionEstablished(second);
        JsonNode welcome = last();
        assertThat(welcome.get("state").asText()).isEqualTo("NEW");
        assertThat(this.handler.activeSessions()).isEqualTo(2);
    }

    @Test
    @DisplayName("транспортная ошибка освобождает сессию")
    void transportErrorReleasesSession() {
        this.handler.handleTransportError(this.session, new IllegalStateException("разрыв"));

        assertThat(this.handler.activeSessions()).isZero();
    }

    @Test
    @DisplayName("частичные сообщения не поддерживаются")
    void partialMessagesDisabled() {
        assertThat(this.handler.supportsPartialMessages()).isFalse();
    }

    @Test
    @DisplayName("закрытие чужой сессии не сбрасывает остальные")
    void closingOneSessionKeepsOthers() throws Exception {
        WebSocketSession second = mock(WebSocketSession.class);
        when(second.getId()).thenReturn("second");
        when(second.isOpen()).thenReturn(true);
        this.handler.afterConnectionEstablished(second);

        this.handler.afterConnectionClosed(second, CloseStatus.NORMAL);

        assertThat(this.handler.activeSessions()).isEqualTo(1);
        this.send("IN_PROGRESS");
        assertThat(last().get("state").asText()).isEqualTo("IN_PROGRESS");
    }
}
