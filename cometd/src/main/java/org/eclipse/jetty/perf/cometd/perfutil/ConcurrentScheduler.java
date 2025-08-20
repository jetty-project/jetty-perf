package org.eclipse.jetty.perf.cometd.perfutil;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.thread.Scheduler;

import static java.util.Objects.requireNonNull;

public class ConcurrentScheduler extends AbstractLifeCycle implements Scheduler
{
    private final int threadsPerScheduler;
    private final ScheduledExecutorService[] schedulers;
    private final String threadBaseName;

    public ConcurrentScheduler(int schedulerCount, int threadsPerScheduler, String threadBaseName)
    {
        if (schedulerCount <= 0)
            throw new IllegalArgumentException("schedulerCount must be at least one");
        this.schedulers = new ScheduledThreadPoolExecutor[schedulerCount];
        if (threadsPerScheduler <= 0)
            throw new IllegalArgumentException("threadsPerScheduler must be at least one");
        this.threadsPerScheduler = threadsPerScheduler;
        this.threadBaseName = requireNonNull(threadBaseName, "threadBaseName is null");
    }

    @Override
    protected void doStart()
    {
        for (int i = 0; i < schedulers.length; i++)
        {
            int finalIdx = i;
            ThreadFactory tf = r ->
            {
                Thread t = new Thread(threadBaseName + "-timeout-%s" + finalIdx);
                t.setDaemon(true);
                return t;
            };
            schedulers[i] = Executors.newScheduledThreadPool(threadsPerScheduler, tf);
        }
    }

    @Override
    protected void doStop()
    {
        for (int i = 0; i < schedulers.length; i++)
        {
            schedulers[i].shutdownNow();
            schedulers[i] = null;
        }
    }

    @Override
    public Task schedule(Runnable task, long delay, TimeUnit unit)
    {
        ScheduledExecutorService scheduler = schedulers[ThreadLocalRandom.current().nextInt(schedulers.length)];
        if (scheduler == null)
            return () -> false;

        ScheduledFuture<?> result = scheduler.schedule(task, delay, unit);
        return () -> result.cancel(false);
    }
}
