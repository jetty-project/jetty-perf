package org.eclipse.jetty.perf.histogram.loader;

import java.io.FileNotFoundException;
import java.util.Timer;
import java.util.TimerTask;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogWriter;
import org.HdrHistogram.Recorder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

public class ResponseTimeListener implements Resource.NodeListener, LoadGenerator.CompleteListener
{
    private final Recorder recorder = new Recorder(3);
    private final Timer timer = new Timer();
    private final HistogramLogWriter writer;

    public ResponseTimeListener() throws FileNotFoundException
    {
        this("perf.hlog");
    }

    public ResponseTimeListener(String histogramFilename) throws FileNotFoundException
    {
        writer = new HistogramLogWriter(histogramFilename);
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

    @Override
    public void onResourceNode(Resource.Info info)
    {
        long responseTime = info.getResponseTime() - info.getRequestTime();
        recorder.recordValue(responseTime);
    }

    @Override
    public void onComplete(LoadGenerator generator)
    {
        timer.cancel();
        writer.outputIntervalHistogram(recorder.getIntervalHistogram());
        writer.close();
    }
}
