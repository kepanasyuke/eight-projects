package ru.multitool.eight.p4conc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Проект 4. Многопоточность: fan-out через CompletableFuture, тайм-ауты, гонка
 * с потерянными обновлениями, дедлок и политика перегрузки пула.
 */
@Service
public class ConcurrencyService {

    private static final Logger LOG = LoggerFactory.getLogger(ConcurrencyService.class);

    private final ThreadPoolExecutor executor;
    private final long taskTimeoutMs;

    public ConcurrencyService(@Qualifier("eightExecutor") ThreadPoolExecutor executor,
                              @Value("${eight.concurrency.task-timeout-ms:1200}") long taskTimeoutMs) {
        this.executor = executor;
        this.taskTimeoutMs = taskTimeoutMs;
    }

    public ParallelReport parallel() {
        long startedAt = System.nanoTime();
        List<CallResult> calls = List.of(
                timed("invoice-api", 150L),
                timed("crypto-api", 400L),
                timed("warehouse-api", 250L)
        );
        List<CompletableFuture<CallResult>> futures = new ArrayList<>();
        for (CallResult call : calls) {
            CompletableFuture<CallResult> future = CompletableFuture
                    .supplyAsync(() -> call, this.executor)
                    .orTimeout(this.taskTimeoutMs, TimeUnit.MILLISECONDS);
            futures.add(future);
        }
        List<CallResult> results = new ArrayList<>();
        for (CompletableFuture<CallResult> future : futures) {
            try {
                results.add(future.join());
            } catch (CompletionException ex) {
                results.add(new CallResult("failed", 0L, "ERROR"));
            }
        }
        long totalMs = (System.nanoTime() - startedAt) / 1_000_000L;
        long sequentialMs = results.stream().mapToLong(CallResult::durationMs).sum();
        ParallelReport report = new ParallelReport(results, totalMs, sequentialMs,
                "Параллельный вызов трёх сервисов быстрее последовательного примерно во столько раз, "
                        + "сколько было одновременных вызовов");
        return report;
    }

    public TimeoutReport timeout() {
        long slowWork = this.taskTimeoutMs + 700L;
        long startedAt = System.nanoTime();
        String status = "OK";
        String message = "Вызов успел";
        try {
            CompletableFuture.supplyAsync(() -> blockingCall(slowWork), this.executor)
                    .orTimeout(this.taskTimeoutMs, TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof TimeoutException) {
                status = "TIMEOUT";
                message = "Задача не уложилась в " + this.taskTimeoutMs + " мс и была отменена";
            } else {
                status = "ERROR";
                message = "Неожиданная ошибка: " + cause.getMessage();
            }
        }
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        TimeoutReport report = new TimeoutReport(slowWork, this.taskTimeoutMs, elapsedMs, status, message);
        return report;
    }

    public RaceReport race(int threads, int iterations) {
        int safeThreads = Math.max(1, Math.min(threads, 8));
        int safeIterations = Math.max(1, Math.min(iterations, 50_000));
        int[] unsafeCounter = {0};
        AtomicInteger safeCounter = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(safeThreads * 2);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int index = 0; index < safeThreads; index++) {
            futures.add(CompletableFuture.runAsync(() -> {
                awaitBarrier(barrier);
                for (int step = 0; step < safeIterations; step++) {
                    unsafeCounter[0]++;
                }
            }, this.executor));
            futures.add(CompletableFuture.runAsync(() -> {
                awaitBarrier(barrier);
                for (int step = 0; step < safeIterations; step++) {
                    safeCounter.incrementAndGet();
                }
            }, this.executor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long expected = (long) safeThreads * safeIterations;
        long lost = expected - unsafeCounter[0];
        RaceReport report = new RaceReport(safeThreads, safeIterations, expected, unsafeCounter[0],
                safeCounter.get(), lost,
                lost > 0
                        ? "Обычный int потерял обновления: инкремент не атомарен без синхронизации"
                        : "Гонки не случилось — на этой машине операции не пересеклись, повторите запуск");
        return report;
    }

    public DeadlockReport deadlock(boolean ordered) {
        ReentrantLock left = new ReentrantLock();
        ReentrantLock right = new ReentrantLock();
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger timedOut = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(2);
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> {
            awaitBarrier(barrier);
            acquirePair(left, right, ordered, completed, timedOut);
        }, this.executor);
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> {
            awaitBarrier(barrier);
            acquirePair(right, left, ordered, completed, timedOut);
        }, this.executor);
        CompletableFuture.allOf(first, second).join();
        DeadlockReport report = new DeadlockReport(ordered, completed.get(), timedOut.get(),
                ordered
                        ? "Оба потока берут блокировки в одном порядке — взаимной блокировки нет"
                        : "Потоки берут блокировки в разном порядке — оба отпустили их по тайм-ауту, "
                        + "именно так выглядит дедлок");
        return report;
    }

    public PoolReport pool(int tasks) {
        int total = Math.max(1, Math.min(tasks, 200));
        AtomicInteger started = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        for (int index = 0; index < total; index++) {
            try {
                this.executor.execute(() -> {
                    started.incrementAndGet();
                    awaitLatch(release);
                    finished.incrementAndGet();
                });
            } catch (RejectedExecutionException ex) {
                rejected.incrementAndGet();
            }
        }
        release.countDown();
        awaitDrain();
        PoolReport report = new PoolReport(total, started.get(), finished.get(), rejected.get(),
                this.executor.getPoolSize(), this.executor.getMaximumPoolSize(),
                this.executor.getQueue().remainingCapacity(),
                "AbortPolicy явно сообщает о перегрузке вместо бесконечного роста очереди");
        return report;
    }

    private void awaitDrain() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        for (int attempt = 0; attempt < 100 && System.nanoTime() < deadline; attempt++) {
            if (this.executor.getActiveCount() == 0 && this.executor.getQueue().isEmpty()) {
                break;
            }
            sleepQuietly(50L);
        }
    }

    private void acquirePair(ReentrantLock first, ReentrantLock second, boolean ordered,
                             AtomicInteger completed, AtomicInteger timedOut) {
        boolean firstAcquired = false;
        boolean secondAcquired = false;
        try {
            firstAcquired = first.tryLock(300L, TimeUnit.MILLISECONDS);
            if (firstAcquired) {
                secondAcquired = second.tryLock(300L, TimeUnit.MILLISECONDS);
            }
            if (firstAcquired && secondAcquired) {
                completed.incrementAndGet();
            } else {
                timedOut.incrementAndGet();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            if (secondAcquired) {
                second.unlock();
            }
            if (firstAcquired) {
                first.unlock();
            }
        }
    }

    private CallResult timed(String name, long workMillis) {
        long startedAt = System.nanoTime();
        sleepQuietly(workMillis);
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        CallResult result = new CallResult(name, durationMs, "OK");
        return result;
    }

    private String blockingCall(long workMillis) {
        sleepQuietly(workMillis);
        return "done";
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.debug("Поток прерван во время ожидания");
        }
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(2, TimeUnit.SECONDS);
        } catch (Exception ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public record CallResult(String name, long durationMs, String status) {
    }

    public record ParallelReport(List<CallResult> calls, long totalMs, long sequentialMs, String comment) {
    }

    public record TimeoutReport(long workMillis, long timeoutMs, long elapsedMs, String status, String message) {
    }

    public record RaceReport(int threads, int iterations, long expected, long unsafeValue, long safeValue,
                             long lostUpdates, String comment) {
    }

    public record DeadlockReport(boolean ordered, int completed, int timedOut, String comment) {
    }

    public record PoolReport(int submitted, int started, int finished, int rejected, int poolSize, int maxPoolSize,
                             int queueRemaining, String comment) {
    }
}
