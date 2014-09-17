//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.perf.http;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.util.Atomics;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class MonitoringQueuedThreadPool extends QueuedThreadPool
{
    private final AtomicLong tasks = new AtomicLong();
    private final AtomicLong maxTaskLatency = new AtomicLong();
    private final AtomicLong totalTaskLatency = new AtomicLong();
    private final AtomicReference<Runnable> maxLatencyTask = new AtomicReference<>();
    private final MonitoringBlockingArrayQueue queue;
    private final AtomicLong maxQueueLatency = new AtomicLong();
    private final AtomicLong totalQueueLatency = new AtomicLong();
    private final AtomicInteger threads = new AtomicInteger();
    private final AtomicInteger maxThreads = new AtomicInteger();

    public MonitoringQueuedThreadPool(int maxThreads)
    {
        // Use a very long idle timeout to avoid creation/destruction of threads
        super(maxThreads, maxThreads, 24 * 3600 * 1000, new MonitoringBlockingArrayQueue(maxThreads, maxThreads));
        queue = (MonitoringBlockingArrayQueue)getQueue();
        setStopTimeout(2000);
    }

    @Override
    public void execute(final Runnable job)
    {
        final long begin = System.nanoTime();
        super.execute(new Runnable()
        {
            public void run()
            {
                long queueLatency = System.nanoTime() - begin;
                tasks.incrementAndGet();
                Atomics.updateMax(maxQueueLatency, queueLatency);
                totalQueueLatency.addAndGet(queueLatency);
                Atomics.updateMax(maxThreads, threads.incrementAndGet());
                long start = System.nanoTime();
                try
                {
                    job.run();
                }
                finally
                {
                    long taskLatency = System.nanoTime() - start;
                    threads.decrementAndGet();
                    if (Atomics.updateMax(maxTaskLatency, taskLatency))
                        maxLatencyTask.compareAndSet(maxLatencyTask.get(), job);
                    totalTaskLatency.addAndGet(taskLatency);
                }
            }
        });
    }

    public void reset()
    {
        tasks.set(0);
        maxTaskLatency.set(0);
        totalTaskLatency.set(0);
        maxLatencyTask.set(null);
        queue.reset();
        maxQueueLatency.set(0);
        totalQueueLatency.set(0);
        threads.set(0);
        maxThreads.set(0);
    }

    public long getTasks()
    {
        return tasks.get();
    }

    public int getMaxActiveThreads()
    {
        return maxThreads.get();
    }

    public int getMaxQueueSize()
    {
        return queue.maxSize.get();
    }

    public long getAverageQueueLatency()
    {
        long count = tasks.get();
        return count == 0 ? -1 : totalQueueLatency.get() / count;
    }

    public long getMaxQueueLatency()
    {
        return maxQueueLatency.get();
    }

    public long getMaxTaskLatency()
    {
        return maxTaskLatency.get();
    }

    public long getAverageTaskLatency()
    {
        long count = tasks.get();
        return count == 0 ? -1 : totalTaskLatency.get() / count;
    }

    public Runnable getMaxLatencyTask()
    {
        return maxLatencyTask.get();
    }

    public static class MonitoringBlockingArrayQueue extends BlockingArrayQueue<Runnable>
    {
        private final AtomicInteger size = new AtomicInteger();
        private final AtomicInteger maxSize = new AtomicInteger();

        public MonitoringBlockingArrayQueue(int capacity, int growBy)
        {
            super(capacity, growBy);
        }

        public void reset()
        {
            size.set(0);
            maxSize.set(0);
        }

        @Override
        public void clear()
        {
            reset();
            super.clear();
        }

        @Override
        public boolean offer(Runnable job)
        {
            boolean added = super.offer(job);
            if (added)
                increment();
            return added;
        }

        private void increment()
        {
            Atomics.updateMax(maxSize, size.incrementAndGet());
        }

        @Override
        public Runnable poll()
        {
            Runnable job = super.poll();
            if (job != null)
                decrement();
            return job;
        }

        @Override
        public Runnable poll(long time, TimeUnit unit) throws InterruptedException
        {
            Runnable job = super.poll(time, unit);
            if (job != null)
                decrement();
            return job;
        }

        @Override
        public Runnable take() throws InterruptedException
        {
            Runnable job = super.take();
            decrement();
            return job;
        }

        private void decrement()
        {
            size.decrementAndGet();
        }
    }
}
