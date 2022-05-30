package org.eclipse.jetty.perf.handler;

import org.eclipse.jetty.perf.util.LatencyRecorder;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpStream;
import org.eclipse.jetty.server.Request;

public class LatencyRecordingHandler extends Handler.Wrapper
{
    private final LatencyRecorder recorder;

    public LatencyRecordingHandler(LatencyRecorder recorder)
    {
        this.recorder = recorder;
    }

    @Override
    public Request.Processor handle(Request request) throws Exception
    {
        long before = System.nanoTime();
        Request.Processor processor = super.handle(request);
        if (processor == null)
            return null;

        request.addHttpStreamWrapper(httpStream -> new HttpStream.Wrapper(httpStream)
        {
            @Override
            public void succeeded()
            {
                super.succeeded();
                recorder.recordValue(System.nanoTime() - before);
            }

            @Override
            public void failed(Throwable x)
            {
                super.failed(x);
                recorder.recordValue(System.nanoTime() - before);
            }
        });

        return processor;
    }
}
