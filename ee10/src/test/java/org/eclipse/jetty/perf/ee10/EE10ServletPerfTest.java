package org.eclipse.jetty.perf.ee10;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.perf.test.ClusteredPerfTest;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class EE10ServletPerfTest
{
    private static final Duration WARMUP_DURATION = Duration.ofSeconds(60);
    private static final Duration RUN_DURATION = Duration.ofSeconds(180);

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
        ClusteredPerfTest.runTest(testName,() ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            ServletContextHandler targetContextHandler = new ServletContextHandler();
            targetContextHandler.setContextPath("/");
            targetContextHandler.addServlet(new AsyncEE10Servlet("Hi there!".getBytes(StandardCharsets.ISO_8859_1)), "/*");
            contextHandlerCollection.addHandler(targetContextHandler);
            ServletContextHandler uselessContextHandler = new ServletContextHandler();
            uselessContextHandler.setContextPath("/useless");
            uselessContextHandler.addServlet(new Always404Servlet(), "/*");
            contextHandlerCollection.addHandler(uselessContextHandler);
            return contextHandlerCollection;
        });
    }

    @Test
    public void testNoGzipSync() throws Exception
    {
        ClusteredPerfTest.runTest(testName, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            ServletContextHandler targetContextHandler = new ServletContextHandler();
            targetContextHandler.setContextPath("/");
            targetContextHandler.addServlet(new SyncEE10Servlet("Hi there!".getBytes(StandardCharsets.ISO_8859_1)), "/*");
            contextHandlerCollection.addHandler(targetContextHandler);
            ServletContextHandler uselessContextHandler = new ServletContextHandler();
            uselessContextHandler.setContextPath("/useless");
            uselessContextHandler.addServlet(new Always404Servlet(), "/*");
            contextHandlerCollection.addHandler(uselessContextHandler);
            return contextHandlerCollection;
        });
    }
}
