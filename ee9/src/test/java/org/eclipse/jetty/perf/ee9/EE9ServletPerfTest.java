package org.eclipse.jetty.perf.ee9;

import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.ee9.servlet.ServletContextHandler;
import org.eclipse.jetty.ee9.servlet.ServletHolder;
import org.eclipse.jetty.perf.test.ClusteredPerfTest;
import org.eclipse.jetty.perf.test.ClusteredTestContext;
import org.eclipse.jetty.perf.test.junit.ClusteredTest;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.jupiter.api.Test;

public class EE9ServletPerfTest
{
    @Test
    public void testNoGzipAsync(@ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        ClusteredPerfTest.runTest(clusteredTestContext, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            ServletContextHandler targetContextHandler = new ServletContextHandler();
            targetContextHandler.setContextPath("/");
            targetContextHandler.addServlet(new ServletHolder(new AsyncEE9Servlet("Hi there!".getBytes(StandardCharsets.ISO_8859_1))), "/*");
            contextHandlerCollection.addHandler(targetContextHandler.getCoreContextHandler());
            ServletContextHandler uselessContextHandler = new ServletContextHandler();
            uselessContextHandler.setContextPath("/useless");
            uselessContextHandler.addServlet(new ServletHolder(new Always404Servlet()), "/*");
            contextHandlerCollection.addHandler(uselessContextHandler.getCoreContextHandler());
            return contextHandlerCollection;
        });
    }

    @Test
    public void testNoGzipSync(@ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        ClusteredPerfTest.runTest(clusteredTestContext, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            ServletContextHandler targetContextHandler = new ServletContextHandler();
            targetContextHandler.setContextPath("/");
            targetContextHandler.addServlet(new ServletHolder(new SyncEE9Servlet("Hi there!".getBytes(StandardCharsets.ISO_8859_1))), "/*");
            contextHandlerCollection.addHandler(targetContextHandler.getCoreContextHandler());
            ServletContextHandler uselessContextHandler = new ServletContextHandler();
            uselessContextHandler.setContextPath("/useless");
            uselessContextHandler.addServlet(new ServletHolder(new Always404Servlet()), "/*");
            contextHandlerCollection.addHandler(uselessContextHandler.getCoreContextHandler());
            return contextHandlerCollection;
        });
    }
}
