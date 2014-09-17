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
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.client.util.ByteBufferContentProvider;
import org.eclipse.jetty.http.HttpHeader;

public class AsyncClient extends AbstractClient
{
    private final HttpClient client;

    public AsyncClient(String host, int port, int connections, int senders) throws Exception
    {
        super(host, port, connections, senders);
        HttpClient client = new HttpClient(new HttpClientTransportOverHTTP(senders), null);
        client.setMaxConnectionsPerDestination(connections);
        this.client = client;
    }

    @Override
    public void connect() throws Exception
    {
        // Connections will be opened as requested and pooled.
        client.start();
    }

    @Override
    public Result benchmark(int iterations, long pause, int length) throws Exception
    {
        Request request = client.newRequest(getHost(), getPort())
                .path("/benchmark/")
                .agent("Benchmark")
                .accept("text/html", "application/xhtml+xml", "application/xml;q=0.9,*/*;q=0.8")
                .header(HttpHeader.ACCEPT_LANGUAGE, "en-US,en;q=0.5")
                .header(HttpHeader.CONTENT_LENGTH, String.valueOf(8))
                .header(HttpHeader.CONTENT_TYPE, "application/octet-stream")
                .header("X-Content-Length", String.valueOf(length));

        int requests = getConnections() * iterations;
        CountDownLatch latch = new CountDownLatch(requests);

        Result result = new Result();

        beginMonitoring();

        long begin = System.nanoTime();
        for (int i = 0; i < requests; ++i)
        {
            ByteBuffer buffer = ByteBuffer.allocateDirect(8);
            buffer.putLong(System.nanoTime()).flip();
            request.content(new ByteBufferContentProvider(buffer));
            request.send(new Listener(latch, result));
            getPlatformTimer().sleep(pause);
        }

        // Wait for all the transactions to be done.
        latch.await();

        long elapsed = System.nanoTime() - begin;
        result.elapsed.set(elapsed);

        endMonitoring();

        // Compute receive throughput when all transactions are done.
        long rcvElapsed = result.rcvEndTime.get() - result.rcvBeginTime.get();
        float throughput = result.precision * result.received.get() * 1_000_000_000F / rcvElapsed;
        result.rcvThroughput.addAndGet((long)throughput);

        return result;
    }

    @Override
    public void disconnect() throws Exception
    {
        client.stop();
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
        try
        {
            client.newRequest(getHost(), getPort())
                    .path("/monitor" + action)
                    .header(HttpHeader.CONNECTION, "close")
                    .send();
            return true;
        }
        catch (Throwable x)
        {
            x.printStackTrace();
            return false;
        }
    }

    private static class Listener extends Response.Listener.Adapter
    {
        private final CountDownLatch latch;
        private final Result result;
        private int cursor = 8;
        private long timestamp;

        private Listener(CountDownLatch latch, Result result)
        {
            this.latch = latch;
            this.result = result;
        }

        @Override
        public void onContent(Response response, ByteBuffer content)
        {
            if (cursor == 8 && content.remaining() >= 8)
            {
                timestamp = content.getLong();
                cursor = 0;
            }
            else
            {
                while (cursor > 0 && content.hasRemaining())
                {
                    --cursor;
                    timestamp += content.get() << cursor * 8;
                }
            }
        }

        @Override
        public void onComplete(org.eclipse.jetty.client.api.Result httpResult)
        {
            long now = System.nanoTime();
            result.rcvBeginTime.compareAndSet(0, now);
            result.rcvEndTime.set(now);
            long latency = now - timestamp;
            result.latency(latency);
            result.received.incrementAndGet();
            latch.countDown();
        }
    }
}
