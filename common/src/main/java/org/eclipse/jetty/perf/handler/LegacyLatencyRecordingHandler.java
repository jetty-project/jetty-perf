package org.eclipse.jetty.perf.handler;

import org.eclipse.jetty.perf.util.LatencyRecorder;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpStream;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

/**
 * Comparable to what 11.0.x does
 */
public class LegacyLatencyRecordingHandler extends Handler.Wrapper
{
    private final LatencyRecorder recorder;

    public LegacyLatencyRecordingHandler(Handler handler, LatencyRecorder recorder)
    {
        super(handler);
        this.recorder = recorder;
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception
    {
        request.addHttpStreamWrapper(httpStream -> new HttpStream.Wrapper(httpStream)
        {
            final long before = System.nanoTime();
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
        return super.handle(request, response, callback);
    }
}
