package org.eclipse.jetty.perf.handler;

import org.eclipse.jetty.perf.test.ClusteredTestContext;
import org.eclipse.jetty.perf.test.Jetty12ClusteredPerfTest;
import org.eclipse.jetty.perf.test.PerfTestParams;
import org.eclipse.jetty.perf.test.junit.ClusteredTest;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.eclipse.jetty.perf.assertions.Assertions.assertExpectationsFromReport;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CoreHandlerPerfTest
{
    private static final int WARMUP_DURATION = 60;
    private static final int RUN_DURATION = 180;

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 200_000, 4,  4_300, 23_000, 10.0",
        "h2c,  100_000, 4, 15_000, 38_000, 15.0"
    })
    public void testNoGzipAsync(String protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin, @ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        PerfTestParams params = new PerfTestParams();
        params.WARMUP_DURATION = WARMUP_DURATION;
        params.RUN_DURATION = RUN_DURATION;
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

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 200_000, 4,  4_300, 25_000, 10.0",
        "h2c,  100_000, 4, 16_000, 38_000, 15.0"
    })
    public void testNoGzipSyncUsingBlocker(String protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin, @ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        PerfTestParams params = new PerfTestParams();
        params.WARMUP_DURATION = WARMUP_DURATION;
        params.RUN_DURATION = RUN_DURATION;
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


    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 200_000, 4,  4_500, 25_000, 10.0",
        "h2c,  100_000, 4, 19_000, 38_000, 15.0"
    })
    public void testNoGzipSyncUsingOutputStream(String protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin, @ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        PerfTestParams params = new PerfTestParams();
        params.WARMUP_DURATION = WARMUP_DURATION;
        params.RUN_DURATION = RUN_DURATION;
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

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 200_000, 4, 3_200, 22_000, 10.0",
        "h2c,  100_000, 4, 9_000, 33_000, 15.0"
    })
    public void testNoGzipFullyAsyncHandlerTree(String protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin, @ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        PerfTestParams params = new PerfTestParams();
        params.WARMUP_DURATION = WARMUP_DURATION;
        params.RUN_DURATION = RUN_DURATION;
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
}
