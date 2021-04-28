//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.mortbay.jetty.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NodeArrayFuture
{
    private final List<CompletableFuture<Object>> futures;

    NodeArrayFuture(List<CompletableFuture<Object>> futures)
    {
        this.futures = futures;
    }

    public void cancel(boolean mayInterruptIfRunning)
    {
        futures.forEach(f -> f.cancel(mayInterruptIfRunning));
    }

    public void get(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException
    {
        boolean noTimeout = timeout == Long.MIN_VALUE && unit == null;
        long timeoutLeft = unit != null ? unit.toNanos(timeout) : -1L;

        TimeoutException timeoutException = null;
        List<Throwable> exceptions = new ArrayList<>();
        for (CompletableFuture<Object> future : futures)
        {
            long begin = System.nanoTime();
            try
            {
                if (noTimeout)
                    future.get();
                else
                    future.get(timeoutLeft, TimeUnit.NANOSECONDS);
            }
            catch (TimeoutException e)
            {
                if (timeoutException == null)
                    timeoutException = e;
                else
                    exceptions.add(e);
            }
            catch (ExecutionException e)
            {
                exceptions.add(e.getCause());
            }
            catch (Exception e)
            {
                exceptions.add(e);
            }
            long delta = System.nanoTime() - begin;
            timeoutLeft = Math.max(timeoutLeft - delta, 0L);
        }

        if (timeoutException != null)
        {
            exceptions.forEach(timeoutException::addSuppressed);
            throw timeoutException;
        }

        if (!exceptions.isEmpty())
        {
            Throwable rootException = exceptions.get(0);
            ExecutionException executionException = (rootException instanceof ExecutionException) ? (ExecutionException)rootException : new ExecutionException(rootException);
            for (int i = 1; i < exceptions.size(); i++)
            {
                Throwable t = exceptions.get(i);
                executionException.addSuppressed(t);
            }
            throw executionException;
        }
    }

    public void get() throws ExecutionException
    {
        try
        {
            get(Long.MIN_VALUE, null);
        }
        catch (TimeoutException e)
        {
            throw new ExecutionException(e);
        }
    }

    public boolean isAllDone()
    {
        return futures.stream()
            .map(Future::isDone)
            .reduce((b1, b2) -> b1 && b2)
            .orElse(true);
    }

    public boolean isAnyDone()
    {
        return futures.stream()
            .map(Future::isDone)
            .reduce((b1, b2) -> b1 || b2)
            .orElse(true);
    }
}
