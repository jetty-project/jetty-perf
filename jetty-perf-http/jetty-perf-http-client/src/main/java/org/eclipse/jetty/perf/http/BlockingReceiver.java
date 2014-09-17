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

import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CyclicBarrier;

public class BlockingReceiver implements Runnable
{
    private final ByteBuffer response = ByteBuffer.allocate(4096);
    private final CyclicBarrier barrier;
    private final SocketChannel socket;
    private final int iterations;
    private final int length;
    private final Parser parser = new Parser();
    private final Result result;

    public BlockingReceiver(CyclicBarrier barrier, SocketChannel socket, int iterations, int length, Result result)
    {
        this.barrier = barrier;
        this.socket = socket;
        this.iterations = iterations;
        this.length = length;
        this.result = result;
        this.response.position(response.limit());
    }

    @Override
    public void run()
    {
        await(barrier);

        int received = 0;
        while (true)
        {
            if (!read())
                break;
            long now = System.nanoTime();
            result.rcvBeginTime.compareAndSet(0, now);
            result.rcvEndTime.set(now);
            long latency = now - parser.timestamp;
            result.latency(latency);
            result.received.incrementAndGet();
            if (++received == iterations)
                break;
        }
    }

    private boolean read()
    {
        try
        {
            while (true)
            {
                // Read what's remaining first, if any.
                if (parser.parse(response))
                    return true;

                response.clear();
                int read = socket.read(response);
                response.flip();

                if (read < 0)
                    return false;
            }
        }
        catch (ClosedByInterruptException x)
        {
            return false;
        }
        catch (Throwable x)
        {
            x.printStackTrace();
            close(socket);
            return false;
        }
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

    private void close(SocketChannel socket)
    {
        try
        {
            socket.close();
        }
        catch (Throwable ignored)
        {
        }
    }

    private class Parser
    {
        private static final String RESPONSE_TEMPLATE = "HTTP/1.1 ";

        private State state = State.PREPARE;
        private int helper;
        private int code;
        private long timestamp;

        private boolean parse(ByteBuffer buffer)
        {
            while (buffer.hasRemaining())
            {
                switch (state)
                {
                    case PREPARE:
                    {
                        if (helper == 0)
                            helper = RESPONSE_TEMPLATE.length();

                        buffer.position(buffer.position() + 1);
                        --helper;
                        if (helper == 0)
                        {
                            code = 0;
                            helper = 100;
                            state = State.RESPONSE;
                        }
                        break;
                    }
                    case RESPONSE:
                    {
                        code += ((buffer.get() & 0xFF) - '0') * helper;
                        helper /= 10;
                        if (helper == 0)
                            state = State.HEADERS;
                        break;
                    }
                    case HEADERS:
                    {
                        // Search for 4 consecutive bytes representing \r\n\r\n,
                        // the end of the HTTP headers.
                        int b = buffer.get() & 0xFF;
                        if (b == '\r' || b == '\n')
                            ++helper;
                        else if (helper > 0)
                            helper = 0;
                        if (helper == 4)
                        {
                            timestamp = 0;
                            helper = 64;
                            state = State.TIMESTAMP;
                        }
                        break;
                    }
                    case TIMESTAMP:
                    {
                        // Read first 8 bytes as the timestamp.
                        helper -= 8;
                        long b = buffer.get() & 0xFF;
                        timestamp += b << helper;
                        if (helper == 0)
                        {
                            helper = length;
                            state = State.CONTENT;
                        }
                        break;
                    }
                    case CONTENT:
                    {
                        int data = Math.min(buffer.remaining(), helper);
                        buffer.position(buffer.position() + data);
                        helper -= data;
                        if (helper == 0)
                        {
                            state = State.PREPARE;
                            return true;
                        }
                        break;
                    }
                    default:
                    {
                        throw new IllegalStateException();
                    }
                }
            }
            return false;
        }
    }

    private enum State
    {
        PREPARE, RESPONSE, HEADERS, TIMESTAMP, CONTENT
    }
}
