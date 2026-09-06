package com.tuling.tim.server.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BeanConfigTest {
    @Test
    void boundedExecutorRejectsInsteadOfRunningOnCaller() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InstrumentedRejectedExecutionHandler handler = new InstrumentedRejectedExecutionHandler();
        handler.bindTo(registry);
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), ExecutorsHolder.threadFactory(), handler);
        try {
            executor.execute(() -> await(release));
            executor.execute(() -> { });
            assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> { }));
            assertEquals(1.0, registry.get("tim.business.executor.rejected").counter().count());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static final class ExecutorsHolder {
        private static java.util.concurrent.ThreadFactory threadFactory() {
            return java.util.concurrent.Executors.defaultThreadFactory();
        }
    }
}
