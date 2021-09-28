package org.eclipse.jetty.perf.histogram.server;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.perf.util.HistogramLogRecorder;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

public class LatencyRecordingChannelListener extends AbstractLifeCycle implements HttpChannel.Listener
{
    private final Map<Request, Long> timestamps = new ConcurrentHashMap<>();
    private final HistogramLogRecorder recorder;

    public LatencyRecordingChannelListener() throws FileNotFoundException
    {
        this("perf.hlog");
    }

    public LatencyRecordingChannelListener(String histogramFilename) throws FileNotFoundException
    {
        this.recorder = new HistogramLogRecorder(histogramFilename, 3, 1000);
    }

    public void startRecording()
    {
        recorder.startRecording();
    }

    public void stopRecording()
    {
        recorder.stopRecording();
    }

    @Override
    protected void doStop()
    {
        stopRecording();
    }

    @Override
    public void onRequestBegin(Request request)
    {
        if (recorder.isRecording())
        {
            long begin = System.nanoTime();
            timestamps.put(request, begin);
        }
    }

    @Override
    public void onComplete(Request request)
    {
        if (recorder.isRecording())
        {
            Long begin = timestamps.remove(request);
            if (begin == null)
                return;
            long responseTime = System.nanoTime() - begin;
            recorder.recordValue(responseTime);
        }
    }
}
