package org.eclipse.jetty.perf.springboot;

import java.io.FileNotFoundException;

import org.eclipse.jetty.perf.test.PerfTestParams;
import org.eclipse.jetty.perf.util.LatencyRecorder;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpStream;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

@Component
public class JettyCustomizer implements WebServerFactoryCustomizer<JettyServletWebServerFactory>
{
    private final LatencyRecorder latencyRecorder;
    private final PerfTestParams perfTestParams;
    private Server server;

    public JettyCustomizer(PerfTestParams perfTestParams) throws FileNotFoundException
    {
        this.perfTestParams = perfTestParams;
        this.latencyRecorder = new LatencyRecorder("perf.hlog");
    }

    public LatencyRecorder getLatencyRecorder()
    {
        return latencyRecorder;
    }

    public Server getServer()
    {
        return server;
    }

    @Override
    public void customize(JettyServletWebServerFactory factory)
    {
        // TODO add additional Jetty configuration
        factory.setPort(perfTestParams.getServerPort());
        factory.addServerCustomizers(server ->
        {
            this.server = server;
            ModernLatencyRecordingHandler latencyRecordingHandler = new ModernLatencyRecordingHandler(latencyRecorder);
            server.insertHandler(latencyRecordingHandler);
        });
    }

    /**
     * Identical to what 11.0.x does
     */
    static class ModernLatencyRecordingHandler extends Handler.Wrapper
    {
        private final LatencyRecorder recorder;

        public ModernLatencyRecordingHandler(LatencyRecorder recorder)
        {
            this.recorder = recorder;
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception
        {
            request.addHttpStreamWrapper(httpStream -> new HttpStream.Wrapper(httpStream)
            {
                @Override
                public void succeeded()
                {
                    super.succeeded();
                    recorder.recordValue(System.nanoTime() - request.getBeginNanoTime());
                }

                @Override
                public void failed(Throwable x)
                {
                    super.failed(x);
                    recorder.recordValue(System.nanoTime() - request.getBeginNanoTime());
                }
            });
            return super.handle(request, response, callback);
        }
    }
}
