package org.eclipse.jetty.perf;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Stream;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.perf.servlet.AsyncServlet;
import org.eclipse.jetty.perf.util.AbstractPerfTest;
import org.eclipse.jetty.perf.util.PerfTestParams;
import org.eclipse.jetty.server.Handler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class AsyncServletPerfTest extends AbstractPerfTest
{
    private static final Duration WARMUP_DURATION = Duration.ofSeconds(6);
    private static final Duration RUN_DURATION = Duration.ofSeconds(18);

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
        ServletContextHandler servletContextHandler = new ServletContextHandler();
        servletContextHandler.setContextPath("/");
        servletContextHandler.addServlet(new AsyncServlet("Hi there!".getBytes(StandardCharsets.ISO_8859_1)), "/*");
        return servletContextHandler;
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testPerf(PerfTestParams params) throws Exception
    {
        runTest(params, WARMUP_DURATION, RUN_DURATION);
    }
}
