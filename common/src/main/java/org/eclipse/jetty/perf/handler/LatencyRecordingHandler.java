package org.eclipse.jetty.perf.handler;

import java.util.function.Function;

import org.eclipse.jetty.perf.util.LatencyRecorder;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpStream;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

public class LatencyRecordingHandler extends Handler.Wrapper
{
    private final LatencyRecorder recorder;
    private final Function<HttpStream, HttpStream> recordingWrapper;

    public LatencyRecordingHandler(LatencyRecorder recorder)
    {
        this.recorder = recorder;
        this.recordingWrapper = httpStream -> new HttpStream.Wrapper(httpStream)
        {
            @Override
            public void succeeded()
            {
                long before = httpStream.getNanoTime();
                super.succeeded();
                LatencyRecordingHandler.this.recorder.recordValue(System.nanoTime() - before);
            }

            @Override
            public void failed(Throwable x)
            {
                long before = httpStream.getNanoTime();
                super.failed(x);
                LatencyRecordingHandler.this.recorder.recordValue(System.nanoTime() - before);
            }
        };
    }

    @Override
    public boolean process(Request request, Response response, Callback callback) throws Exception
    {
        request.addHttpStreamWrapper(recordingWrapper);
        return super.process(request, response, callback);
    }
}
