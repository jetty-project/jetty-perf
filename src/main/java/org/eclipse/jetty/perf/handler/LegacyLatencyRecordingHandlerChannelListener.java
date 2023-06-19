package org.eclipse.jetty.perf.handler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.perf.util.HistogramLogRecorder;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * Comparable to what 12.0.x does
 */
public abstract class LegacyLatencyRecordingHandlerChannelListener extends AbstractHandler implements HttpChannel.Listener
{
    private final Map<Request, Long> timestamps = new ConcurrentHashMap<>();
    private final HistogramLogRecorder recorder;

    public LegacyLatencyRecordingHandlerChannelListener() throws Exception
    {
        this("perf.hlog");
    }

    public LegacyLatencyRecordingHandlerChannelListener(String histogramFilename) throws FileNotFoundException
    {
        this(new HistogramLogRecorder(histogramFilename, 3, 1000));
    }

    public LegacyLatencyRecordingHandlerChannelListener(HistogramLogRecorder recorder)
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
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        long begin = System.nanoTime();
        timestamps.put(baseRequest, begin);
    }

    @Override
    public void onComplete(Request request)
    {
        Long begin = timestamps.remove(request);
        if (begin == null)
            return;
        long responseTime = System.nanoTime() - begin;
        recorder.recordValue(responseTime);
    }
}
