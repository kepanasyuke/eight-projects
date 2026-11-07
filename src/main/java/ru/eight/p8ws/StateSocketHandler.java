package ru.eight.p8ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ru.eight.domain.WorkItemStateMachine;
import ru.eight.domain.WorkItemStatus;

/**
 * Проект 8. Серверная машина состояний поверх WebSocket: жизненный цикл соединения,
 * валидация команд, ограничение размера сообщений и освобождение ресурсов при разрыве.
 */
@Component
public class StateSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(StateSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final int maxPayloadLength;
    private final long sendTimeLimitMs;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public StateSocketHandler(ObjectMapper objectMapper,
                              @Value("${eight.websocket.max-text-message-bytes:8192}") int maxPayloadLength,
                              @Value("${eight.websocket.send-time-limit-ms:10000}") long sendTimeLimitMs) {
        this.objectMapper = objectMapper;
        this.maxPayloadLength = maxPayloadLength;
        this.sendTimeLimitMs = sendTimeLimitMs;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession decorator = new ConcurrentWebSocketSessionDecorator(session, (int) this.sendTimeLimitMs,
                64 * 1024);
        this.sessions.put(session.getId(), new SessionState(decorator));
        LOG.info("WebSocket открыт: {}", session.getId());
        sendWelcome(decorator);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SessionState state = this.sessions.get(session.getId());
        if (state == null) {
            sendError(session, "NOT_REGISTERED", "Соединение не зарегистрировано сервером");
        } else {
            processMessage(state, message);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        LOG.warn("Транспортная ошибка WebSocket {}: {}", session.getId(), exception.getMessage());
        closeQuietly(session, CloseStatus.SERVER_ERROR);
        this.sessions.remove(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        this.sessions.remove(session.getId());
        LOG.info("WebSocket закрыт: {} ({})", session.getId(), status.getCode());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    public int activeSessions() {
        return this.sessions.size();
    }

    private void processMessage(SessionState state, TextMessage message) {
        if (message.getPayloadLength() > this.maxPayloadLength) {
            sendError(state.session(), "MESSAGE_TOO_LARGE",
                    "Сообщение длиннее " + this.maxPayloadLength + " байт и отклонено");
        } else {
            CommandFrame frame = readFrame(message.getPayload());
            if (frame == null || frame.command() == null) {
                sendError(state.session(), "MALFORMED_FRAME", "Ожидается JSON вида {\"command\":\"IN_PROGRESS\"}");
            } else {
                applyCommand(state, frame);
            }
        }
    }

    private void applyCommand(SessionState state, CommandFrame frame) {
        WorkItemStatus target = parseStatus(frame.command());
        if (target == null) {
            sendError(state.session(), "UNKNOWN_COMMAND", "Команда " + frame.command() + " неизвестна");
        } else if (!WorkItemStateMachine.canTransition(state.current(), target)) {
            sendError(state.session(), "INVALID_TRANSITION",
                    "Переход " + state.current() + " -> " + target + " недопустим");
        } else {
            WorkItemStatus next = WorkItemStateMachine.transition(state.current(), target);
            state.move(next, frame.note());
            sendState(state);
        }
    }

    private CommandFrame readFrame(String payload) {
        CommandFrame frame = null;
        try {
            frame = this.objectMapper.readValue(payload, CommandFrame.class);
        } catch (IOException ex) {
            LOG.debug("Некорректный JSON от клиента: {}", ex.getMessage());
        }
        return frame;
    }

    private WorkItemStatus parseStatus(String command) {
        return Arrays.stream(WorkItemStatus.values())
                .filter(candidate -> candidate.name().equalsIgnoreCase(command))
                .findFirst()
                .orElse(null);
    }

    private void sendWelcome(WebSocketSession session) {
        WelcomeFrame frame = new WelcomeFrame("welcome", session.getId(), WorkItemStatus.NEW.name(),
                names(WorkItemStateMachine.allowedTargets(WorkItemStatus.NEW)), this.maxPayloadLength);
        send(session, frame);
    }

    private void sendState(SessionState state) {
        StateFrame frame = new StateFrame("state", state.current().name(),
                names(WorkItemStateMachine.allowedTargets(state.current())), List.copyOf(state.history()));
        send(state.session(), frame);
    }

    private void sendError(WebSocketSession session, String code, String text) {
        ErrorFrame frame = new ErrorFrame("error", code, text);
        send(session, frame);
    }

    private void send(WebSocketSession session, Object frame) {
        try {
            String json = this.objectMapper.writeValueAsString(frame);
            session.sendMessage(new TextMessage(json));
        } catch (IOException ex) {
            LOG.warn("Не удалось отправить кадр в {}: {}", session.getId(), ex.getMessage());
            closeQuietly(session, CloseStatus.SERVER_ERROR);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException ex) {
            LOG.debug("Соединение {} уже закрыто", session.getId());
        }
    }

    private static List<String> names(List<WorkItemStatus> statuses) {
        List<String> values = new ArrayList<>();
        for (WorkItemStatus status : statuses) {
            values.add(status.name());
        }
        return List.copyOf(values);
    }

    private static final class SessionState {

        private final WebSocketSession session;
        private final List<String> history = new ArrayList<>();
        private WorkItemStatus current = WorkItemStatus.NEW;

        private SessionState(WebSocketSession session) {
            this.session = session;
            this.history.add("NEW");
        }

        WebSocketSession session() {
            return this.session;
        }

        WorkItemStatus current() {
            return this.current;
        }

        List<String> history() {
            return this.history;
        }

        void move(WorkItemStatus next, String note) {
            this.current = next;
            String entry = note == null || note.isBlank() ? next.name() : next.name() + " (" + note + ")";
            this.history.add(entry);
        }
    }

    public record CommandFrame(String command, String note) {
    }

    public record WelcomeFrame(String type, String sessionId, String state, List<String> allowed, int maxPayloadBytes) {
    }

    public record StateFrame(String type, String state, List<String> allowed, List<String> history) {
    }

    public record ErrorFrame(String type, String code, String message) {
    }
}
