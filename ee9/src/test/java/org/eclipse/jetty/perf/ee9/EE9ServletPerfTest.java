package org.eclipse.jetty.perf.ee9;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Stream;

import org.eclipse.jetty.ee9.servlet.ServletContextHandler;
import org.eclipse.jetty.ee9.servlet.ServletHolder;
import org.eclipse.jetty.perf.test.FlatPerfTest;
import org.eclipse.jetty.perf.test.PerfTestParams;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EE9ServletPerfTest
{
    private static final Duration WARMUP_DURATION = Duration.ofSeconds(60);
    private static final Duration RUN_DURATION = Duration.ofSeconds(180);

    private static Stream<PerfTestParams> params()
    {
        // TODO these figures are dependent upon the protocol *and* the test -> there should be a way to adjust the rates, expected latencies and error margin.
        return Stream.of(
            new PerfTestParams(PerfTestParams.Protocol.http, 60_000, 100, 4_000, 625_000, 15.0)
//            new PerfTestParams(PerfTestParams.Protocol.https, 60_000, 100, 5_500, 1_150_000, 15.0)
//            new PerfTestParams(PerfTestParams.Protocol.h2c, 60_000, 100, 8_500, 650_000, 15.0),
//            new PerfTestParams(PerfTestParams.Protocol.h2, 60_000, 100, 90_000, 1_000_000, 15.0)
        );
    }

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

    @ParameterizedTest
    @MethodSource("params")
    @Disabled
    public void testComplete(PerfTestParams params) throws Exception
    {
        boolean succeeded = FlatPerfTest.runTest(testName, params, WARMUP_DURATION, RUN_DURATION, () ->
        {
            GzipHandler gzipHandler = new GzipHandler();
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            gzipHandler.setHandler(contextHandlerCollection);
            ServletContextHandler targetContextHandler = new ServletContextHandler();
            targetContextHandler.setContextPath("/");
            targetContextHandler.addServlet(new ServletHolder(new AsyncEE9Servlet("Hi there!".getBytes(StandardCharsets.ISO_8859_1))), "/*");
            contextHandlerCollection.addHandler(targetContextHandler.getCoreContextHandler());
            ServletContextHandler uselessContextHandler = new ServletContextHandler();
            uselessContextHandler.setContextPath("/useless");
            uselessContextHandler.addServlet(new ServletHolder(new Always404Servlet()), "/*");
            contextHandlerCollection.addHandler(uselessContextHandler.getCoreContextHandler());
            return gzipHandler;
        });
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testNoGzipAsync(PerfTestParams params) throws Exception
    {
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

    @ParameterizedTest
    @MethodSource("params")
    public void testNoGzipSync(PerfTestParams params) throws Exception
    {
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
