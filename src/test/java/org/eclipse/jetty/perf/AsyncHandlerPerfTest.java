package org.eclipse.jetty.perf;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Stream;

import org.eclipse.jetty.perf.handler.AsyncHandler;
import org.eclipse.jetty.perf.util.AbstractPerfTest;
import org.eclipse.jetty.perf.util.PerfTestParams;
import org.eclipse.jetty.server.Handler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class AsyncHandlerPerfTest extends AbstractPerfTest
{
    private static final Duration WARMUP_DURATION = Duration.ofSeconds(60);
    private static final Duration RUN_DURATION = Duration.ofSeconds(180);

    private static Stream<PerfTestParams> params()
    {
        return Stream.of(
            new PerfTestParams(PerfTestParams.Protocol.http),
            new PerfTestParams(PerfTestParams.Protocol.https),
            new PerfTestParams(PerfTestParams.Protocol.h2c),
            new PerfTestParams(PerfTestParams.Protocol.h2)
        );
    }

    @Override
    protected Handler createHandler()
    {
        return new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1));
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testPerf(PerfTestParams params) throws Exception
    {
        runTest(params, WARMUP_DURATION, RUN_DURATION);
    }
}
