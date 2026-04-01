package org.eclipse.jetty.perf.handler;

import org.eclipse.jetty.perf.test.ClusteredTestContext;
import org.eclipse.jetty.perf.test.Jetty12ClusteredPerfTest;
import org.eclipse.jetty.perf.test.PerfTestParams;
import org.eclipse.jetty.perf.test.junit.ClusteredTest;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.eclipse.jetty.perf.assertions.Assertions.assertExpectationsFromReport;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CoreHandlerPerfTest
{
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 100_000, 4,  5_500, 35_000, 10.0",
        "h2c,  100_000, 4, 25_000, 50_000, 15.0"
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
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            AsyncHandler asyncHandler = new AsyncHandler("Hi there!".getBytes(US_ASCII));
            targetContextHandler.setHandler(asyncHandler);
            return contextHandlerCollection;
        });
        boolean succeeded = assertExpectationsFromReport(clusteredTestContext, params, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @Disabled
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 100_000, 4,  5_500, 35_000, 10.0",
        "h2c,  100_000, 4, 15_000, 38_000, 15.0"
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
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            AsyncHandler asyncHandler = new AsyncHandler("Hi there!".getBytes(US_ASCII));
            targetContextHandler.setHandler(asyncHandler);
            return contextHandlerCollection;
        });
        boolean succeeded = assertExpectationsFromReport(clusteredTestContext, params, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 100_000, 4,  4_000, 31_000, 10.0",
        "h2c,  100_000, 4, 10_500, 31_000, 15.0"
    })
    public void testPCAsync(String protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin, @ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        PerfTestParams params = new PerfTestParams();
        params.HTTP_PROTOCOL = protocol;
        params.LOADER_RATE = loaderRate;
        params.LOADER_THREADS = loaderThreads;
        Jetty12ClusteredPerfTest.runTest(clusteredTestContext, params, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection(false);
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            AsyncHandler asyncHandler = new AsyncHandler("Hi there!".getBytes(US_ASCII));
            targetContextHandler.setHandler(asyncHandler);
            return contextHandlerCollection;
        });
        boolean succeeded = assertExpectationsFromReport(clusteredTestContext, params, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @Disabled
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 100_000, 4,  4_300, 25_000, 10.0",
        "h2c,  100_000, 4, 16_000, 38_000, 15.0"
    })
    public void testEPCSyncUsingBlocker(String protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin, @ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        PerfTestParams params = new PerfTestParams();
        params.HTTP_PROTOCOL = protocol;
        params.LOADER_RATE = loaderRate;
        params.LOADER_THREADS = loaderThreads;
        Jetty12ClusteredPerfTest.runTest(clusteredTestContext, params, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            SyncHandlerUsingBlocker syncHandler = new SyncHandlerUsingBlocker("Hi there!".getBytes(US_ASCII));
            targetContextHandler.setHandler(syncHandler);
            return contextHandlerCollection;
        });
        boolean succeeded = assertExpectationsFromReport(clusteredTestContext, params, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @Disabled
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 100_000, 4,  4_500, 25_000, 10.0",
        "h2c,  100_000, 4, 19_000, 38_000, 15.0"
    })
    public void testEPCSyncUsingOutputStream(String protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin, @ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        PerfTestParams params = new PerfTestParams();
        params.HTTP_PROTOCOL = protocol;
        params.LOADER_RATE = loaderRate;
        params.LOADER_THREADS = loaderThreads;
        Jetty12ClusteredPerfTest.runTest(clusteredTestContext, params, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            SyncHandlerUsingOutputStream syncHandler = new SyncHandlerUsingOutputStream("Hi there!".getBytes(US_ASCII));
            targetContextHandler.setHandler(syncHandler);
            return contextHandlerCollection;
        });
        boolean succeeded = assertExpectationsFromReport(clusteredTestContext, params, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }
}
