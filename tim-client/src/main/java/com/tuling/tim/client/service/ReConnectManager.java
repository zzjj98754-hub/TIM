package com.tuling.tim.client.service;

import com.tuling.tim.client.thread.ReConnectJob;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.ChannelHandlerContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @since JDK 1.8
 */
@Component
public final class ReConnectManager {

    private ScheduledExecutorService scheduledExecutorService;
    private final AtomicInteger attempts = new AtomicInteger();

    /**
     * Trigger reconnect job
     * @param ctx
     */
    public void reConnect(ChannelHandlerContext ctx) {
        buildExecutor() ;
        int attempt = attempts.getAndIncrement();
        long base = Math.min(30L, 1L << Math.min(attempt, 5));
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, base / 4 + 1));
        scheduledExecutorService.schedule(new ReConnectJob(ctx, this), base + jitter, TimeUnit.SECONDS);
    }

    /**
     * Close reconnect job if reconnect success.
     */
    public void reConnectSuccess(){
        attempts.set(0);
        if (scheduledExecutorService != null) scheduledExecutorService.shutdown();
    }


    /***
     * build an thread executor
     * @return
     */
    private ScheduledExecutorService buildExecutor() {
        if (scheduledExecutorService == null || scheduledExecutorService.isShutdown()) {
            ThreadFactory sche = new ThreadFactoryBuilder()
                    .setNameFormat("reConnect-job-%d")
                    .setDaemon(true)
                    .build();
            scheduledExecutorService = new ScheduledThreadPoolExecutor(1, sche);
            return scheduledExecutorService;
        } else {
            return scheduledExecutorService;
        }
    }
}
