package org.eclipse.jetty.perf.ee9;

import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.ee9.servlet.ServletContextHandler;
import org.eclipse.jetty.ee9.servlet.ServletHolder;
import org.eclipse.jetty.perf.test.FlatPerfTest;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class EE9ServletPerfTest
{
    private String testName;

    @BeforeEach
    protected void beforeEach(TestInfo testInfo)
    {
        // Generate test name
        String className = testInfo.getTestClass().orElseThrow().getName();
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        String methodName = testInfo.getTestMethod().orElseThrow().getName();
        testName = simpleClassName + "_" + methodName;
    }

    @Test
    public void testNoGzipAsync() throws Exception
    {
        FlatPerfTest.runTest(testName, () ->
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
    public void testNoGzipSync() throws Exception
    {
        FlatPerfTest.runTest(testName, () ->
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
