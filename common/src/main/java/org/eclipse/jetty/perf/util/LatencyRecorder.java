package org.eclipse.jetty.perf.util;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.util.Timer;
import java.util.TimerTask;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogWriter;
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;

public class LatencyRecorder implements org.eclipse.jetty.perf.util.Recorder
{
    private final HistogramLogRecorder recorder;

    public LatencyRecorder(String histogramFilename) throws FileNotFoundException
    {
        this.recorder = new HistogramLogRecorder(histogramFilename, 3, 1000);
    }

    @Override
    public void startRecording()
    {
        recorder.startRecording();
    }

    @Override
    public void stopRecording()
    {
        recorder.close();
    }

    public LifeCycle asLifeCycle()
    {
        return new AbstractLifeCycle() {
            @Override
            protected void doStop()
            {
                stopRecording();
            }
        };
    }

    public void recordValue(long value)
    {
        recorder.recordValue(value);
    }

    private static class HistogramLogRecorder implements Closeable
    {
        private enum State
        {
            NOT_RECORDING, RECORDING, CLOSED
        }

        private final Recorder recorder;
        private final Timer timer = new Timer();
        private final HistogramLogWriter writer;
        private volatile State state = State.NOT_RECORDING;

        public HistogramLogRecorder(String histogramFilename, int numberOfSignificantValueDigits, int intervalInMs) throws FileNotFoundException
        {
            this.recorder = new Recorder(numberOfSignificantValueDigits);
            this.writer = new HistogramLogWriter(histogramFilename);
            timer.schedule(new TimerTask()
            {
                private Histogram intervalHistogram;
                @Override
                public void run()
                {
                    intervalHistogram = recorder.getIntervalHistogram(intervalHistogram);
                    if (state == State.RECORDING)
                    {
                        writer.outputIntervalHistogram(intervalHistogram);
                    }
                }
            }, intervalInMs, intervalInMs);
        }

        public void startRecording()
        {
            if (state != State.NOT_RECORDING)
                throw new IllegalStateException("current state: " + state);

            long now = System.currentTimeMillis();
            writer.setBaseTime(now);
            writer.outputBaseTime(now);
            writer.outputStartTime(now);
            state = State.RECORDING;
        }

        @Override
        public void close()
        {
            if (state == State.CLOSED)
                return;
            state = State.CLOSED;

            timer.cancel();
            writer.close();
        }

        public void recordValue(long value)
        {
            recorder.recordValue(value);
        }
    }
}
