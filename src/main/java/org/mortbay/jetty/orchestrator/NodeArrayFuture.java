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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    public void get(long timeout, TimeUnit unit) throws ExecutionException
    {
        List<Exception> exceptions = new ArrayList<>();
        for (CompletableFuture<Object> future : futures)
        {
            try
            {
                if (timeout == Long.MIN_VALUE && unit == null)
                    future.get();
                else
                    future.get(timeout, unit);
            }
            catch (Exception e)
            {
                exceptions.add(e);
            }
        }

        if (!exceptions.isEmpty())
        {
            Exception exception = exceptions.get(0);
            for (int i = 1; i < exceptions.size(); i++)
            {
                Throwable t = exceptions.get(i);
                exception.addSuppressed(t);
            }
            throw new ExecutionException(exception);
        }
    }

    public void get() throws CancellationException, ExecutionException
    {
        get(Long.MIN_VALUE, null);
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
