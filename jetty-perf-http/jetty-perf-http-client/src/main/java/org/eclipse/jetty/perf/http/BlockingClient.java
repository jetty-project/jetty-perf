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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BlockingClient extends AbstractClient
{
    private final ExecutorService executor;
    private final SocketChannel[] sockets;

    public BlockingClient(String host, int port, int connections, int senders) throws Exception
    {
        super(host, port, connections, senders);
        int threads = connections + senders;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable task)
            {
                Thread thread = new Thread(task);
                thread.setName("client-" + thread.getId());
                return thread;
            }
        });
        executor.prestartAllCoreThreads();
        this.executor = executor;
        this.sockets = new SocketChannel[connections];
    }

    @Override
    public void connect() throws Exception
    {
        for (int i = 0; i < sockets.length; ++i)
            sockets[i] = newSocket();
    }

    public void disconnect() throws Exception
    {
        executor.shutdownNow();
        executor.awaitTermination(16, TimeUnit.SECONDS);

        for (SocketChannel socket : sockets)
            socket.close();
    }

    protected SocketChannel newSocket() throws IOException
    {
        SocketChannel socket = SocketChannel.open(new InetSocketAddress(getHost(), getPort()));
        socket.setOption(StandardSocketOptions.TCP_NODELAY, true);
        return socket;
    }

    public Result benchmark(int iterations, long pause, int length) throws Exception
    {
        int senders = getSenders();
        int connections = getConnections();
        CyclicBarrier senderBarrier = new CyclicBarrier(senders + 1);
        CyclicBarrier receiverBarrier = new CyclicBarrier(connections + 1);

        Result result = new Result();

        int index = 0;
        for (int i = 1; i <= senders; i++)
        {
            // Each sender will work on a partition of the sockets.
            int chunk = connections / senders;
            if (i == senders)
                chunk = connections - index;
            SocketChannel[] channels = Arrays.copyOfRange(sockets, index, index + chunk);
            BlockingSender sender = new BlockingSender(senderBarrier, getPlatformTimer(), channels, getHost(), getPort(), iterations, pause, length, result);
            executor.execute(sender);

            // The receivers for the partition.
            for (SocketChannel channel : channels)
            {
                BlockingReceiver receiver = new BlockingReceiver(receiverBarrier, channel, iterations, length, result);
                executor.execute(receiver);
            }

            index += chunk;
        }

        // Wait for all the receivers to be ready.
        receiverBarrier.await();

        long begin = System.nanoTime();

        // Wait for all the senders to be ready.
        senderBarrier.await();

        // Wait for all the transactions to be done.
        senderBarrier.await();

        long elapsed = System.nanoTime() - begin;
        result.elapsed.set(elapsed);

        // Compute receive throughput when all transactions are done.
        long rcvElapsed = result.rcvEndTime.get() - result.rcvBeginTime.get();
        float throughput = result.precision * result.received.get() * 1_000_000_000F / rcvElapsed;
        result.rcvThroughput.addAndGet((long)throughput);

        return result;
    }
}
