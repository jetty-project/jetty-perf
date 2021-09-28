package org.eclipse.jetty.perf.util;

import java.io.FileNotFoundException;
import java.util.Timer;
import java.util.TimerTask;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogWriter;
import org.HdrHistogram.Recorder;

public class HistogramLogRecorder
{
    private final int intervalInMs;
    private final int numberOfSignificantValueDigits;
    private final Recorder recorder;
    private final Timer timer = new Timer();
    private final HistogramLogWriter writer;
    private volatile boolean recording;

    public HistogramLogRecorder(String histogramFilename, int numberOfSignificantValueDigits, int intervalInMs) throws FileNotFoundException
    {
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
        this.intervalInMs = intervalInMs;
        this.recorder = new Recorder(numberOfSignificantValueDigits);
        this.writer = new HistogramLogWriter(histogramFilename);
    }

    public void startRecording()
    {
        if (recording)
            return;
        long now = System.currentTimeMillis();
        recorder.reset();
        writer.setBaseTime(now);
        writer.outputBaseTime(now);
        writer.outputStartTime(now);
        timer.schedule(new TimerTask()
        {
            private final Histogram h = new Histogram(numberOfSignificantValueDigits);

            @Override
            public void run()
            {
                recorder.getIntervalHistogramInto(h);
                writer.outputIntervalHistogram(h);
                h.reset();
            }
        }, intervalInMs, intervalInMs);
        recording = true;
    }

    public void stopRecording()
    {
        if (!recording)
            return;
        recording = false;
        timer.cancel();
        writer.close();
    }

    public boolean isRecording()
    {
        return recording;
    }

    public void recordValue(long value)
    {
        recorder.recordValue(value);
    }
}
