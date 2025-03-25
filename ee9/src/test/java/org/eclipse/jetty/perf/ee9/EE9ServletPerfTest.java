package org.eclipse.jetty.perf.ee9;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.eclipse.jetty.ee9.servlet.ServletContextHandler;
import org.eclipse.jetty.ee9.servlet.ServletHolder;
import org.eclipse.jetty.perf.test.FlatPerfTest;
import org.eclipse.jetty.perf.test.PerfTestParams;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EE9ServletPerfTest
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

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 60_000, 1,  5_500, 800_000, 15.0",
        "h2c,  60_000, 2, 23_000, 850_000, 15.0"
    })
    public void testNoGzipAsync(PerfTestParams.Protocol protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin) throws Exception
    {
        PerfTestParams params = new PerfTestParams(protocol, loaderRate, loaderThreads, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        boolean succeeded = FlatPerfTest.runTest(testName, params, WARMUP_DURATION, RUN_DURATION, () ->
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
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 60_000, 1,  5_500,  30_000, 10.0",
        "h2c,  60_000, 2, 23_000, 850_000, 15.0"
    })
    public void testNoGzipSync(PerfTestParams.Protocol protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin) throws Exception
    {
        PerfTestParams params = new PerfTestParams(protocol, loaderRate, loaderThreads, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        boolean succeeded = FlatPerfTest.runTest(testName, params, WARMUP_DURATION, RUN_DURATION, () ->
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
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }
}
