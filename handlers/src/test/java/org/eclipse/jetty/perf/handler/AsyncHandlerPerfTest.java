package org.eclipse.jetty.perf.handler;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Stream;

import org.eclipse.jetty.perf.test.FlatPerfTest;
import org.eclipse.jetty.perf.test.PerfTestParams;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DelayedHandler;
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
        return Stream.of(
            new PerfTestParams(PerfTestParams.Protocol.http),
//            new PerfTestParams(PerfTestParams.Protocol.https),
            new PerfTestParams(PerfTestParams.Protocol.h2c)
//            new PerfTestParams(PerfTestParams.Protocol.h2)
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
            DelayedHandler.UntilContent untilContentHandler = new DelayedHandler.UntilContent();
            GzipHandler gzipHandler = new GzipHandler();
            untilContentHandler.setHandler(gzipHandler);
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            gzipHandler.setHandler(contextHandlerCollection);
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            AsyncHandler asyncHandler = new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1));
            targetContextHandler.addHandler(asyncHandler);
            return untilContentHandler;
        });
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testNoDelay(PerfTestParams params) throws Exception
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
            targetContextHandler.addHandler(asyncHandler);
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
            DelayedHandler.UntilContent untilContentHandler = new DelayedHandler.UntilContent();
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            untilContentHandler.setHandler(contextHandlerCollection);
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            AsyncHandler asyncHandler = new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1));
            targetContextHandler.addHandler(asyncHandler);
            return untilContentHandler;
        });
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testNoDelayNorGzip(PerfTestParams params) throws Exception
    {
        boolean succeeded = FlatPerfTest.runTest(testName, params, WARMUP_DURATION, RUN_DURATION, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            AsyncHandler asyncHandler = new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1));
            targetContextHandler.addHandler(asyncHandler);
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
