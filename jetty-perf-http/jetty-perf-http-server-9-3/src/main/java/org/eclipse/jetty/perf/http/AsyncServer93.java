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

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.toolchain.perf.PlatformMonitor;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class AsyncServer93 extends AbstractServer
{
    public AsyncServer93(int maxThreads, int port, int selectors)
    {
        super(maxThreads, port, selectors);
    }

    @Override
    public void accept() throws Exception
    {
//        final AtomicLong savedWakeups = new AtomicLong();
//        final AtomicLong updates = new AtomicLong();
//        final AtomicLong savedInterests = new AtomicLong();
//        final AtomicLong submits = new AtomicLong();

        QueuedThreadPool threadPool = new QueuedThreadPool(getMaxThreads());
//        final MonitoringQueuedThreadPool threadPool = new MonitoringQueuedThreadPool(getMaxThreads());
        threadPool.setName("server");

        Server server = new Server(threadPool);

        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendDateHeader(false);
        httpConfig.setSendServerVersion(false);
        ServerConnector connector = new ServerConnector(server, 1, getSelectors(), new HttpConnectionFactory(httpConfig));
//        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig))
//        {
//            @Override
//            protected SelectorManager newSelectorManager(Executor executor, Scheduler scheduler, int selectors)
//            {
//                return new ServerConnectorManager(executor, scheduler, selectors)
//                {
//                    @Override
//                    protected ManagedSelector newSelector(int id)
//                    {
//                        return new ManagedSelector(id)
//                        {
//                            @Override
//                            public boolean updateKey(Runnable update)
//                            {
//                                updates.incrementAndGet();
//                                boolean result = super.updateKey(update);
//                                if (!result)
//                                    savedWakeups.incrementAndGet();
//                                return result;
//                            }
//                        };
//                    }
//                };
//            }
//
//            @Override
//            protected SelectChannelEndPoint newEndPoint(SocketChannel channel, SelectorManager.ManagedSelector selectSet, SelectionKey key) throws IOException
//            {
//                return new SelectChannelEndPoint(channel, selectSet, key, getScheduler(), getIdleTimeout())
//                {
//                    @Override
//                    protected void submitKeyUpdate(boolean submit)
//                    {
//                        submits.incrementAndGet();
//                        super.submitKeyUpdate(submit);
//                        if (!submit)
//                            savedInterests.incrementAndGet();
//                    }
//                };
//            }
//        };
        connector.setPort(getPort());
        connector.setIdleTimeout(30 * 60 * 1000);
        server.addConnector(connector);

        ContextHandlerCollection contexts = new ContextHandlerCollection();
        server.setHandler(contexts);

        final StatisticsHandler stats = new StatisticsHandler();
        contexts.addHandler(stats);

        // Add a context used only to start/stop the server monitoring.
        ContextHandler benchmark = new ContextHandler();
        benchmark.setContextPath("/monitor");
        benchmark.setHandler(new BenchmarkHandler()
        {
            @Override
            protected void onStart(PlatformMonitor.Start start)
            {
//                threadPool.reset();
                stats.statsReset();
//                savedWakeups.set(0);
//                updates.set(0);
//                savedInterests.set(0);
//                submits.set(0);
                super.onStart(start);
            }

            @Override
            protected void onStop(PlatformMonitor.Stop stop)
            {
                super.onStop(stop);
                System.err.printf("requests times avg/max: %f.3/%d%n", stats.getDispatchedTimeMean(), stats.getDispatchedTimeMax());
                System.err.printf("max concurrent requests: %d%n", stats.getDispatchedActiveMax());
                System.err.printf("bandwidth: %f.3%n", stop.mebiBytes(stats.getResponsesBytesTotal()) / stats.getStatsOnMs());
//                System.err.printf("saved wakeups: %s/%s | saved interests: %s/%s%n", savedWakeups, updates, savedInterests, submits);
//                System.err.printf("thread pool - tasks = %d | concurrent threads max = %d | queue size max = %d | queue latency avg/max = %d/%d ms | task latency avg/max = %d/%d ms%n",
//                        threadPool.getTasks(),
//                        threadPool.getMaxActiveThreads(),
//                        threadPool.getMaxQueueSize(),
//                        TimeUnit.NANOSECONDS.toMillis(threadPool.getAverageQueueLatency()),
//                        TimeUnit.NANOSECONDS.toMillis(threadPool.getMaxQueueLatency()),
//                        TimeUnit.NANOSECONDS.toMillis(threadPool.getAverageTaskLatency()),
//                        TimeUnit.NANOSECONDS.toMillis(threadPool.getMaxTaskLatency()));
//                System.err.printf("thread pool - max latency task: %s%n", threadPool.getMaxLatencyTask());
            }
        });
        contexts.addHandler(benchmark);

        // We measure the stats only for the benchmark handler.
        ServletContextHandler context = new ServletContextHandler(stats, "/benchmark", false, false);

        String baseDir = System.getProperty("baseDir", System.getProperty("user.dir"));
        if (baseDir.endsWith("/"))
            baseDir = baseDir.substring(baseDir.length() - 1);
        context.setResourceBase(baseDir + "../src/main/resources");
        context.addServlet(DefaultServlet.class, "/");
        context.addServlet(BenchmarkServlet.class, "/*");

        server.start();
    }
}
