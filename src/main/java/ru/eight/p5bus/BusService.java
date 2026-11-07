package ru.eight.p5bus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Проект 5. Сценарии обмена сообщениями: публикация, группы потребителей,
 * повторы с ограничением, DLQ, дедупликация и Transactional Outbox.
 */
@Service
public class BusService {

    private static final String TOPIC = "orders";
    private static final String GROUP = "order-processor";
    private static final String POISON_MARKER = "boom";

    private final InMemoryBroker broker;
    private final OutboxPublisher outbox;

    public BusService(InMemoryBroker broker, OutboxPublisher outbox) {
        this.broker = broker;
        this.outbox = outbox;
    }

    public InMemoryBroker.DeliveryReport runScenario(boolean withPoison, int eventCount) {
        int total = Math.max(1, Math.min(eventCount, 30));
        this.broker.createTopic(TOPIC, 3);
        List<String> publishedIds = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            String key = "order-" + index;
            String payload = "{\"orderId\":" + index + ",\"amount\":" + (index * 100 + 500) + "}";
            String messageId = this.broker.publish(TOPIC, key, payload);
            publishedIds.add(messageId);
        }
        if (!publishedIds.isEmpty()) {
            int lastIndex = publishedIds.size() - 1;
            String duplicatePayload = "{\"orderId\":" + lastIndex + ",\"amount\":" + (lastIndex * 100 + 500) + "}";
            this.broker.publishWithId(TOPIC, "order-" + lastIndex, duplicatePayload, publishedIds.get(lastIndex));
        }
        if (withPoison) {
            this.broker.publish(TOPIC, "order-poison", "{\"orderId\":-1,\"payload\":\"" + POISON_MARKER + "\"}");
        }
        Function<String, Boolean> handler = payload -> !payload.contains(POISON_MARKER);
        InMemoryBroker.DeliveryReport report = this.broker.poll(TOPIC, GROUP, 50, handler);
        return report;
    }

    public BrokerState state() {
        Map<String, Integer> partitionSizes = this.broker.snapshot(TOPIC);
        Map<String, Long> positions = this.broker.consumerPositions(TOPIC);
        BrokerState state = new BrokerState(TOPIC, partitionSizes, positions, this.broker.deadLetters(),
                this.outbox.pendingCount(), this.outbox.latest());
        return state;
    }

    public OutboxPublisher.OutboxView enqueueOutbox(String payload) {
        String safePayload = payload == null || payload.isBlank()
                ? "{\"event\":\"order.paid\",\"orderId\":42,\"at\":\"" + java.time.Instant.now() + "\"}"
                : payload;
        OutboxPublisher.OutboxView view = this.outbox.enqueue("payment-events", safePayload);
        return view;
    }

    public record BrokerState(String topic, Map<String, Integer> partitionSizes, Map<String, Long> consumerPositions,
                              List<String> deadLetters, long pendingOutbox, List<OutboxMessage> outboxMessages) {
    }
}
