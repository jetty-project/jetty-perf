package org.eclipse.jetty.perf.handler;

import java.time.Duration;
import org.eclipse.jetty.perf.test.FlatPerfTest;
import org.eclipse.jetty.perf.test.PerfTestParams;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CoreHandlerPerfTest
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
        "http, 60_000, 1,  4_300, 23_000, 10.0",
        "h2c,  60_000, 2, 15_000, 38_000, 15.0"
    })
    public void testNoGzipAsync(PerfTestParams.Protocol protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin) throws Exception
    {
        PerfTestParams params = new PerfTestParams(protocol, loaderRate, loaderThreads, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        boolean succeeded = FlatPerfTest.runTest(testName, params, WARMUP_DURATION, RUN_DURATION, () ->
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
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 60_000, 1,  4_300, 25_000, 10.0",
        "h2c,  60_000, 2, 16_000, 38_000, 15.0"
    })
    public void testNoGzipSyncUsingBlocker(PerfTestParams.Protocol protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin) throws Exception
    {
        PerfTestParams params = new PerfTestParams(protocol, loaderRate, loaderThreads, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        boolean succeeded = FlatPerfTest.runTest(testName, params, WARMUP_DURATION, RUN_DURATION, () ->
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
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 60_000, 1,  4_500, 25_000, 10.0",
        "h2c,  60_000, 2, 19_000, 38_000, 15.0"
    })
    public void testNoGzipSyncUsingOutputStream(PerfTestParams.Protocol protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin) throws Exception
    {
        PerfTestParams params = new PerfTestParams(protocol, loaderRate, loaderThreads, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        boolean succeeded = FlatPerfTest.runTest(testName, params, WARMUP_DURATION, RUN_DURATION, () ->
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
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "http, 60_000, 1, 3_200, 22_000, 10.0",
        "h2c,  60_000, 2, 9_000, 33_000, 15.0"
    })
    public void testNoGzipFullyAsyncHandlerTree(PerfTestParams.Protocol protocol, int loaderRate, int loaderThreads, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin) throws Exception
    {
        PerfTestParams params = new PerfTestParams(protocol, loaderRate, loaderThreads, expectedP99ServerLatency, expectedP99ProbeLatency, expectedP99ErrorMargin);
        boolean succeeded = FlatPerfTest.runTest(testName, params, WARMUP_DURATION, RUN_DURATION, () ->
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
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }
}
