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

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.toolchain.perf.PlatformMonitor;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class Jetty8SelectServer
{
    public static void main(String[] args) throws Exception
    {
        Server server = new Server();

        server.setSendDateHeader(false);
        server.setSendServerVersion(false);

        QueuedThreadPool pool = new QueuedThreadPool(512);
        pool.setName("server");
        server.setThreadPool(pool);

        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(8080);
        connector.setMaxIdleTime(30 * 60 * 1000);
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
                stats.statsReset();
                super.onStart(start);
            }

            @Override
            protected void onStop(PlatformMonitor.Stop stop)
            {
                super.onStop(stop);
                System.err.printf("max requests: %d%n", stats.getDispatchedActiveMax());
                System.err.printf("bytes downloaded: %d%n", stats.getResponsesBytesTotal());
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
