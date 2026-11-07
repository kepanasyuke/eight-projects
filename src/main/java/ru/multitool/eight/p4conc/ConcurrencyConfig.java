package ru.multitool.eight.p4conc;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Проект 4. Пул потоков с ограниченной очередью и явной политикой перегрузки.
 * AbortPolicy выбран намеренно: лучше вернуть отказ, чем незаметно положить задачи в бесконечную очередь.
 */
@Configuration
public class ConcurrencyConfig {

    @Bean(name = "eightExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor eightExecutor(@Value("${eight.concurrency.pool-size:8}") int poolSize,
                                           @Value("${eight.concurrency.queue-capacity:64}") int queueCapacity) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new NamedThreadFactory("eight-worker-"),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        executor.prestartAllCoreThreads();
        return executor;
    }

    static final class NamedThreadFactory implements ThreadFactory {

        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, this.prefix + this.counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
