package org.eclipse.jetty.perf.handler;

import java.io.FileNotFoundException;

import org.eclipse.jetty.perf.util.HistogramLogRecorder;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * Identical to what 12.0.x does
 */
public abstract class ModernLatencyRecordingHandlerChannelListener extends AbstractHandler implements HttpChannel.Listener
{
    private final HistogramLogRecorder recorder;

    public ModernLatencyRecordingHandlerChannelListener() throws Exception
    {
        this("perf.hlog");
    }

    public ModernLatencyRecordingHandlerChannelListener(String histogramFilename) throws FileNotFoundException
    {
        this(new HistogramLogRecorder(histogramFilename, 3, 1000));
    }

    public ModernLatencyRecordingHandlerChannelListener(HistogramLogRecorder recorder)
    {
        this.recorder = recorder;
    }

    public void startRecording()
    {
        recorder.startRecording();
    }

    public void stopRecording()
    {
        recorder.close();
    }

    @Override
    protected void doStop()
    {
        stopRecording();
    }

    @Override
    public void onComplete(Request request)
    {
        long responseTime = System.nanoTime() - request.getBeginNanoTime();
        recorder.recordValue(responseTime);
    }
}
