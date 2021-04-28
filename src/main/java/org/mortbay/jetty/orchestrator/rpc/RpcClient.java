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

package org.mortbay.jetty.orchestrator.rpc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.mortbay.jetty.orchestrator.rpc.command.Command;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.queue.SimpleDistributedQueue;

public class RpcClient implements AutoCloseable
{
    private final SimpleDistributedQueue commandQueue;
    private final SimpleDistributedQueue responseQueue;
    private final ExecutorService executorService;
    private final ConcurrentMap<Long, CompletableFuture<Object>> calls = new ConcurrentHashMap<>();
    private final AtomicLong requestIdGenerator = new AtomicLong();

    public RpcClient(CuratorFramework curator, String nodeId)
    {
        commandQueue = new SimpleDistributedQueue(curator, "/clients/" + nodeId + "/commandQ");
        responseQueue = new SimpleDistributedQueue(curator, "/clients/" + nodeId + "/responseQ");
        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() ->
        {
            while (true)
            {
                byte[] respBytes = responseQueue.take();
                Response resp = (Response)deserialize(respBytes);
                CompletableFuture<Object> future = calls.remove(resp.getId());
                if (resp.getThrowable() != null)
                    future.completeExceptionally(new ExecutionException(resp.getThrowable()));
                else
                    future.complete(resp.getResult());
            }
        });
    }

    public CompletableFuture<Object> callAsync(Command command) throws Exception
    {
        long requestId = requestIdGenerator.getAndIncrement();
        CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        calls.put(requestId, completableFuture);
        Request request = new Request(requestId, command);
        byte[] cmdBytes = serialize(request);
        commandQueue.offer(cmdBytes);
        return completableFuture;
    }

    public Object call(Command command) throws Exception
    {
        CompletableFuture<Object> future = callAsync(command);
        return future.get();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException
    {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
        return ois.readObject();
    }

    private static byte[] serialize(Object obj) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        return baos.toByteArray();
    }

    @Override
    public void close()
    {
        executorService.shutdownNow();
    }
}
