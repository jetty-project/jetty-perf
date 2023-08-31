package org.eclipse.jetty.perf.handler;

import org.eclipse.jetty.perf.util.LatencyRecorder;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpStream;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

/**
 * Identical to what 11.0.x does
 */
public class ModernLatencyRecordingHandler extends Handler.Wrapper
{
    private final LatencyRecorder recorder;

    public ModernLatencyRecordingHandler(Handler handler, LatencyRecorder recorder)
    {
        super(handler);
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
