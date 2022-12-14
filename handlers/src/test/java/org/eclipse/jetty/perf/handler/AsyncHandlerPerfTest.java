package org.eclipse.jetty.perf.handler;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Stream;

import org.eclipse.jetty.perf.test.AbstractPerfTest;
import org.eclipse.jetty.perf.test.PerfTestParams;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DelayedHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AsyncHandlerPerfTest extends AbstractPerfTest
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

    @Override
    protected Handler createHandler()
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
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testPerf(PerfTestParams params) throws Exception
    {
        boolean succeeded = runTest(params, WARMUP_DURATION, RUN_DURATION);
        assertThat("Performance assertions failure for " + params, succeeded, is(true));
    }
}
