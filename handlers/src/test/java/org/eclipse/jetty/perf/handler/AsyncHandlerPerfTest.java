package org.eclipse.jetty.perf.handler;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Stream;

import org.eclipse.jetty.perf.test.FlatPerfTest;
import org.eclipse.jetty.perf.test.PerfTestParams;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AsyncHandlerPerfTest
{
    private static final Duration WARMUP_DURATION = Duration.ofSeconds(60);
    private static final Duration RUN_DURATION = Duration.ofSeconds(180);

    private static Stream<PerfTestParams> params()
    {
        // TODO these figures are dependent upon the protocol *and* the test -> there should be a way to adjust the rates, expected latencies and error margin.
        return Stream.of(
            new PerfTestParams(PerfTestParams.Protocol.http, 60_000, 100, 4_000, 625_000, 10.0),
            new PerfTestParams(PerfTestParams.Protocol.https, 60_000, 100, 6_000, 1_150_000, 15.0),
            new PerfTestParams(PerfTestParams.Protocol.h2c, 60_000, 100, 8_500, 650_000, 15.0),
            new PerfTestParams(PerfTestParams.Protocol.h2, 60_000, 100, 90_000, 1_000_000, 15.0)
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
    public void testComplete(PerfTestParams params) throws Exception
    {
        boolean succeeded = FlatPerfTest.runTest(testName, params, WARMUP_DURATION, RUN_DURATION, () ->
        {
            GzipHandler gzipHandler = new GzipHandler();
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            gzipHandler.setHandler(contextHandlerCollection);
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            AsyncHandler asyncHandler = new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1));
            targetContextHandler.setHandler(asyncHandler);
            return gzipHandler;
        });
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testNoGzip(PerfTestParams params) throws Exception
    {
        boolean succeeded = FlatPerfTest.runTest(testName, params, WARMUP_DURATION, RUN_DURATION, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            AsyncHandler asyncHandler = new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1));
            targetContextHandler.setHandler(asyncHandler);
            return contextHandlerCollection;
        });
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testAlone(PerfTestParams params) throws Exception
    {
        boolean succeeded = FlatPerfTest.runTest(testName, params, WARMUP_DURATION, RUN_DURATION, () -> new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1)));
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }
}
