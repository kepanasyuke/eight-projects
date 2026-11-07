package ru.multitool.eight.p5bus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Проект 5. Учебный брокер сообщений в памяти: топики, партиции, группы потребителей,
 * смещения, подтверждения, ограниченные повторы, DLQ и дедупликация по идентификатору.
 *
 * <p>Модель повторяет семантику Kafka/RabbitMQ в объёме, нужном для отработки контрактов
 * обработчика, не претендуя на производительность или гарантии доставки боевого брокера.
 */
@Component
public class InMemoryBroker {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryBroker.class);

    private final int maxAttempts;
    private final long retryDelayMs;
    private final Map<String, Topic> topics = new ConcurrentHashMap<>();
    private final Map<String, ConsumerGroup> groups = new ConcurrentHashMap<>();
    private final List<String> deadLetters = new CopyOnWriteArrayList<>();

    public InMemoryBroker(@Value("${eight.broker.retry-max-attempts:3}") int maxAttempts,
                          @Value("${eight.broker.retry-delay-ms:50}") long retryDelayMs) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMs = Math.max(0L, retryDelayMs);
    }

    public Topic createTopic(String name, int partitionCount) {
        Topic topic = this.topics.computeIfAbsent(name, key -> new Topic(key, Math.max(1, partitionCount)));
        return topic;
    }

    public String publish(String topicName, String key, String payload) {
        String messageId = "msg-" + System.nanoTime();
        Topic topic = createTopic(topicName, 3);
        topic.append(key, payload, messageId);
        LOG.debug("Опубликовано сообщение {} в топик {}", messageId, topicName);
        return messageId;
    }

    public String publishWithId(String topicName, String key, String payload, String messageId) {
        Topic topic = createTopic(topicName, 3);
        topic.append(key, payload, messageId);
        return messageId;
    }

    public ConsumerGroup ensureGroup(String topicName, String groupName) {
        String compositeKey = topicName + "#" + groupName;
        ConsumerGroup group = this.groups.computeIfAbsent(compositeKey,
                key -> new ConsumerGroup(groupName, topicName));
        return group;
    }

    public DeliveryReport poll(String topicName, String groupName, int maxMessages,
                               Function<String, Boolean> handler) {
        Topic topic = createTopic(topicName, 3);
        ConsumerGroup group = ensureGroup(topicName, groupName);
        int delivered = 0;
        int acknowledged = 0;
        int retried = 0;
        int deadLettered = 0;
        int deduplicated = 0;
        List<String> events = new ArrayList<>();

        for (int partitionIndex = 0; partitionIndex < topic.partitionCount(); partitionIndex++) {
            List<Stored> partition = topic.partition(partitionIndex);
            int position = group.position(partitionIndex);
            int available = Math.min(Math.max(maxMessages, 1), partition.size() - position);
            for (int step = 0; step < available; step++) {
                Stored message = partition.get(position);
                position++;
                delivered++;
                if (group.isProcessed(message.id())) {
                    deduplicated++;
                    events.add("дубликат " + message.id() + " пропущен: уже обработан");
                } else {
                    boolean handled = false;
                    int attempt = 0;
                    for (int tryIndex = 0; tryIndex < this.maxAttempts && !handled; tryIndex++) {
                        attempt = tryIndex + 1;
                        handled = Boolean.TRUE.equals(handler.apply(message.payload()));
                        if (!handled) {
                            retried++;
                            events.add("сообщение " + message.id() + ": попытка " + attempt + " из "
                                    + this.maxAttempts + " не удалась");
                            sleepQuietly(this.retryDelayMs);
                        }
                    }
                    if (handled) {
                        acknowledged++;
                        group.markProcessed(message.id());
                        events.add("сообщение " + message.id() + " подтверждено после " + attempt + " попытки");
                    } else {
                        deadLettered++;
                        this.deadLetters.add(message.payload());
                        events.add("сообщение " + message.id() + " исчерпало попытки и ушло в DLQ");
                    }
                }
                group.commit(partitionIndex, position);
            }
        }
        DeliveryReport report = new DeliveryReport(topicName, groupName, delivered, acknowledged, retried,
                deadLettered, deduplicated, this.deadLetters.size(), maxAttempts, events, snapshot(topicName));
        return report;
    }

    public Map<String, Integer> snapshot(String topicName) {
        Topic topic = createTopic(topicName, 3);
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (int partitionIndex = 0; partitionIndex < topic.partitionCount(); partitionIndex++) {
            sizes.put("partition-" + partitionIndex, topic.partition(partitionIndex).size());
        }
        return sizes;
    }

    public List<String> deadLetters() {
        return List.copyOf(this.deadLetters);
    }

    public Map<String, Long> consumerPositions(String topicName) {
        Map<String, Long> positions = new LinkedHashMap<>();
        for (Map.Entry<String, ConsumerGroup> entry : this.groups.entrySet()) {
            if (entry.getKey().startsWith(topicName + "#")) {
                positions.put(entry.getValue().name(), entry.getValue().totalPosition());
            }
        }
        return positions;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public record Stored(String id, String key, String payload, Instant publishedAt, long offset) {
    }

    public record DeliveryReport(String topic, String group, int delivered, int acknowledged, int retried,
                                 int deadLettered, int deduplicated, int deadLetterQueueSize, int maxAttempts,
                                 List<String> events, Map<String, Integer> partitionSizes) {
    }

    static final class Topic {

        private final String name;
        private final List<List<Stored>> partitions;
        private final AtomicLong sequence = new AtomicLong();

        Topic(String name, int partitionCount) {
            this.name = name;
            List<List<Stored>> created = new ArrayList<>();
            for (int index = 0; index < partitionCount; index++) {
                created.add(Collections.synchronizedList(new ArrayList<>()));
            }
            this.partitions = List.copyOf(created);
        }

        String name() {
            return this.name;
        }

        int partitionCount() {
            return this.partitions.size();
        }

        List<Stored> partition(int index) {
            return this.partitions.get(index);
        }

        void append(String key, String payload, String messageId) {
            int partitionIndex = Math.floorMod(key == null ? 0 : key.hashCode(), this.partitions.size());
            List<Stored> partition = this.partitions.get(partitionIndex);
            synchronized (partition) {
                long offset = this.sequence.incrementAndGet();
                partition.add(new Stored(messageId, key, payload, Instant.now(), offset));
            }
        }
    }

    static final class ConsumerGroup {

        private final String name;
        private final String topicName;
        private final int[] positions;
        private final Set<String> processed = ConcurrentHashMap.newKeySet();

        ConsumerGroup(String name, String topicName) {
            this.name = name;
            this.topicName = topicName;
            this.positions = new int[64];
        }

        String name() {
            return this.name;
        }

        String topicName() {
            return this.topicName;
        }

        int position(int partitionIndex) {
            return partitionIndex < this.positions.length ? this.positions[partitionIndex] : 0;
        }

        void commit(int partitionIndex, int position) {
            if (partitionIndex < this.positions.length) {
                this.positions[partitionIndex] = position;
            }
        }

        boolean isProcessed(String messageId) {
            return this.processed.contains(messageId);
        }

        void markProcessed(String messageId) {
            this.processed.add(messageId);
        }

        long totalPosition() {
            long total = 0L;
            for (int position : this.positions) {
                total += position;
            }
            return total;
        }
    }
}
