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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.toolchain.perf.PlatformTimer;

public class BlockingSender implements Runnable
{
    private final ByteBuffer[] buffers = new ByteBuffer[2];
    private final CyclicBarrier barrier;
    private final PlatformTimer timer;
    private final SocketChannel[] sockets;
    private final String host;
    private final int port;
    private final int iterations;
    private final long pause;
    private final Result result;

    public BlockingSender(CyclicBarrier barrier, PlatformTimer timer, SocketChannel[] sockets, String host, int port, int iterations, long pause, int responseSize, Result result)
    {
        this.barrier = barrier;
        this.timer = timer;
        this.sockets = sockets;
        this.host = host;
        this.port = port;
        this.iterations = iterations;
        this.pause = pause;
        this.result = result;

        String request = "" +
                "GET /benchmark/ HTTP/1.1\r\n" +
                "Host: " + host + ":" + port + "\r\n" +
                "User-Agent: Benchmark\r\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n" +
                "Accept-Language: en-US,en;q=0.5\r\n" +
                "Accept-Encoding: gzip\r\n" +
                "Content-Length: 8\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "X-Content-Length: " + responseSize + "\r\n" +
                "\r\n";

        // Use a direct buffer to optimize writes.
        byte[] bytes = request.getBytes(Charset.forName("UTF-8"));
        this.buffers[0] = ByteBuffer.allocateDirect(bytes.length);
        this.buffers[0].put(bytes).flip();
        // Buffer for the timestamp (8 bytes).
        this.buffers[1] = ByteBuffer.allocateDirect(8);
    }

    @Override
    public void run()
    {
        await(barrier);

        beginMonitoring();

        long sent = 0;
        long begin = System.nanoTime();
        for (int i = 0; i < iterations; i++)
            sent += send();
        long elapsed = System.nanoTime() - begin;

        result.sent.addAndGet(sent);
        float throughput = result.precision * sent * 1_000_000_000F / elapsed;
        result.sndThroughput.addAndGet((long)throughput);

        // Wait for all responses to arrive before going to the next step.
        int maxWaits = 20;
        int waits = maxWaits;
        long previous = 0;
        long current = result.received.get();
        while (current < sent)
        {
            timer.sleep(TimeUnit.MILLISECONDS.toMicros(250));
            previous = current;
            current = result.received.get();
            if (previous == current)
                --waits;
            else
                waits = maxWaits;
            if (waits == 0)
                break;
        }

        endMonitoring();

        await(barrier);
    }

    private void await(CyclicBarrier barrier)
    {
        try
        {
            barrier.await();
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    private long send()
    {
        long sent = 0;
        for (SocketChannel socket : sockets)
        {
            buffers[0].clear();
            buffers[1].clear();
            buffers[1].putLong(System.nanoTime()).flip();
            if (write(socket, buffers))
                ++sent;
            timer.sleep(pause);
        }
        return sent;
    }

    private boolean write(SocketChannel socket, ByteBuffer[] buffers)
    {
        try
        {
            if (!socket.isOpen())
                return false;
            socket.write(buffers);
            return true;
        }
        catch (Throwable x)
        {
            close(socket);
            return false;
        }
    }

    private boolean beginMonitoring()
    {
        return monitor("/start");
    }

    private boolean endMonitoring()
    {
        return monitor("/stop");
    }

    private boolean monitor(String action)
    {
        SocketChannel monitor = null;
        try
        {
            String request = "" +
                    "GET /monitor" + action + " HTTP/1.1\r\n" +
                    "Host: " + host + ":" + port + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";
            monitor = SocketChannel.open(new InetSocketAddress(host, port));
            monitor.write(ByteBuffer.wrap(request.getBytes("UTF-8")));
            ByteBuffer response = ByteBuffer.allocate(1024);
            while (monitor.read(response) >= 0)
                response.clear();
            return true;
        }
        catch (Throwable x)
        {
            x.printStackTrace();
            return false;
        }
        finally
        {
            close(monitor);
        }
    }

    private void close(SocketChannel socket)
    {
        try
        {
            if (socket != null)
                socket.close();
        }
        catch (Throwable ignored)
        {
        }
    }
}
