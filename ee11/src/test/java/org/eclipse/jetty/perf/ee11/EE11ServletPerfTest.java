package org.eclipse.jetty.perf.ee11;

import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.perf.test.ClusteredTestContext;
import org.eclipse.jetty.perf.test.Jetty12ClusteredPerfTest;
import org.eclipse.jetty.perf.test.PerfTestParams;
import org.eclipse.jetty.perf.test.junit.ClusteredTest;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.eclipse.jetty.perf.assertions.Assertions.assertExpectationsFromReport;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EE11ServletPerfTest
{
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 100_000, 4,  6_600, 40_000, 10.0",
        "h2c,  100_000, 4, 30_000, 55_000, 15.0"
    })
    public void testEPCAsync(String protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin, @ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        PerfTestParams params = new PerfTestParams();
        params.HTTP_PROTOCOL = protocol;
        params.LOADER_RATE = loaderRate;
        params.LOADER_THREADS = loaderThreads;
        Jetty12ClusteredPerfTest.runTest(clusteredTestContext, params, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            ServletContextHandler targetContextHandler = new ServletContextHandler();
            targetContextHandler.setContextPath("/");
            targetContextHandler.addServlet(new AsyncEE11Servlet("Hi there!".getBytes(StandardCharsets.ISO_8859_1)), "/*");
            contextHandlerCollection.addHandler(targetContextHandler);
            ServletContextHandler uselessContextHandler = new ServletContextHandler();
            uselessContextHandler.setContextPath("/useless");
            uselessContextHandler.addServlet(new Always404Servlet(), "/*");
            contextHandlerCollection.addHandler(uselessContextHandler);
            return contextHandlerCollection;
        });
        boolean succeeded = assertExpectationsFromReport(clusteredTestContext, params, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @Disabled
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 100_000, 4,  5_000, 25_000, 10.0",
        "h2c,  100_000, 4, 18_000, 38_000, 15.0"
    })
    public void testPECAsync(String protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin, @ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        PerfTestParams params = new PerfTestParams();
        params.HTTP_PROTOCOL = protocol;
        params.LOADER_RATE = loaderRate;
        params.LOADER_THREADS = loaderThreads;
        params.SERVER_RESERVED_THREADS = 0;
        Jetty12ClusteredPerfTest.runTest(clusteredTestContext, params, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            ServletContextHandler targetContextHandler = new ServletContextHandler();
            targetContextHandler.setContextPath("/");
            targetContextHandler.addServlet(new AsyncEE11Servlet("Hi there!".getBytes(StandardCharsets.ISO_8859_1)), "/*");
            contextHandlerCollection.addHandler(targetContextHandler);
            ServletContextHandler uselessContextHandler = new ServletContextHandler();
            uselessContextHandler.setContextPath("/useless");
            uselessContextHandler.addServlet(new Always404Servlet(), "/*");
            contextHandlerCollection.addHandler(uselessContextHandler);
            return contextHandlerCollection;
        });
        boolean succeeded = assertExpectationsFromReport(clusteredTestContext, params, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @Disabled
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 100_000, 4,  5_000, 25_000, 10.0",
        "h2c,  100_000, 4, 15_000, 38_000, 15.0"
    })
    public void testEPCSync(String protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin, @ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        PerfTestParams params = new PerfTestParams();
        params.HTTP_PROTOCOL = protocol;
        params.LOADER_RATE = loaderRate;
        params.LOADER_THREADS = loaderThreads;
        Jetty12ClusteredPerfTest.runTest(clusteredTestContext, params, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            ServletContextHandler targetContextHandler = new ServletContextHandler();
            targetContextHandler.setContextPath("/");
            targetContextHandler.addServlet(new SyncEE11Servlet("Hi there!".getBytes(StandardCharsets.ISO_8859_1)), "/*");
            contextHandlerCollection.addHandler(targetContextHandler);
            ServletContextHandler uselessContextHandler = new ServletContextHandler();
            uselessContextHandler.setContextPath("/useless");
            uselessContextHandler.addServlet(new Always404Servlet(), "/*");
            contextHandlerCollection.addHandler(uselessContextHandler);
            return contextHandlerCollection;
        });
        boolean succeeded = assertExpectationsFromReport(clusteredTestContext, params, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }
}
