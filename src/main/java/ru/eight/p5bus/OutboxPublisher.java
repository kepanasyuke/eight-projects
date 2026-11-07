package ru.eight.p5bus;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Публикация события строго после коммита транзакции. Планировщик добирает записи,
 * которые остались в PENDING после падения процесса между коммитом и отправкой.
 */
@Service
public class OutboxPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final InMemoryBroker broker;

    public OutboxPublisher(OutboxRepository outbox, InMemoryBroker broker) {
        this.outbox = outbox;
        this.broker = broker;
    }

    @Transactional
    public OutboxView enqueue(String topic, String payload) {
        OutboxMessage message = this.outbox.save(new OutboxMessage(topic, payload));
        Long messageId = message.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishAndMark(messageId, topic, payload);
            }
        });
        OutboxView view = new OutboxView(messageId, topic, payload, "PENDING", true,
                "Событие сохранено в БД и будет опубликовано после коммита");
        return view;
    }

    @Scheduled(fixedDelayString = "${eight.broker.outbox-poll-interval-ms:1000}",
            initialDelayString = "${eight.broker.outbox-poll-interval-ms:1000}")
    public void relayPending() {
        List<OutboxMessage> pending = this.outbox.findTop20ByStatusOrderByIdAsc(OutboxMessage.Status.PENDING);
        for (OutboxMessage message : pending) {
            publishAndMark(message.getId(), message.getTopic(), message.getPayload());
        }
    }

    public List<OutboxMessage> latest() {
        return this.outbox.findTop10ByOrderByIdDesc();
    }

    public long pendingCount() {
        return this.outbox.countByStatus(OutboxMessage.Status.PENDING);
    }

    private void publishAndMark(Long messageId, String topic, String payload) {
        this.broker.publish(topic, String.valueOf(messageId), payload);
        this.outbox.findById(messageId).ifPresent(message -> {
            message.markSent();
            this.outbox.save(message);
        });
        LOG.info("Событие {} опубликовано в топик {}", messageId, topic);
    }

    public record OutboxView(Long id, String topic, String payload, String status, boolean publishedAfterCommit,
                             String comment) {
    }
}
