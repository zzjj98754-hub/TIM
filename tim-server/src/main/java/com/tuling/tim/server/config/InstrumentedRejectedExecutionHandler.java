package com.tuling.tim.server.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/** Rejects overload instead of running blocking work on a Netty event loop. */
final class InstrumentedRejectedExecutionHandler implements RejectedExecutionHandler, MeterBinder {
    private Counter rejected;

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        if (rejected != null) rejected.increment();
        throw new RejectedExecutionException("TIM business executor queue is full");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        rejected = Counter.builder("tim.business.executor.rejected")
                .description("Tasks rejected because the bounded TIM business queue is full")
                .register(registry);
    }
}
