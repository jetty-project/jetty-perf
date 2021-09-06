package org.eclipse.jetty.perf.histogram.server;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogWriter;
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

public class LatencyRecordingChannelListener extends AbstractLifeCycle implements HttpChannel.Listener
{
    private final Map<Request, Long> timestamps = new ConcurrentHashMap<>();
    private final Recorder recorder = new Recorder(3);
    private final Timer timer = new Timer();
    private final HistogramLogWriter writer;
    private boolean record;

    public LatencyRecordingChannelListener() throws FileNotFoundException
    {
        this("perf.hlog");
    }

    public LatencyRecordingChannelListener(String histogramFilename) throws FileNotFoundException
    {
        writer = new HistogramLogWriter(histogramFilename);
    }

    public void startRecording()
    {
        this.record = true;
        long now = System.currentTimeMillis();
        writer.setBaseTime(now);
        writer.outputBaseTime(now);
        writer.outputStartTime(now);
        timer.schedule(new TimerTask()
        {
            private final Histogram h = new Histogram(3);

            @Override
            public void run()
            {
                recorder.getIntervalHistogramInto(h);
                writer.outputIntervalHistogram(h);
                h.reset();
            }
        }, 1000, 1000);
    }

    public void stopRecording()
    {
        timer.cancel();
        writer.close();
    }

    @Override
    protected void doStop()
    {
        stopRecording();
    }

    @Override
    public void onRequestBegin(Request request)
    {
        if (record)
        {
            long begin = System.nanoTime();
            timestamps.put(request, begin);
        }
    }

    @Override
    public void onComplete(Request request)
    {
        if (record)
        {
            Long begin = timestamps.remove(request);
            if (begin == null)
                return;
            long responseTime = System.nanoTime() - begin;
            recorder.recordValue(responseTime);
        }
    }
}
