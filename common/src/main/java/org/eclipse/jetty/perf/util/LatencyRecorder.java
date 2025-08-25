package org.eclipse.jetty.perf.util;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import java.util.concurrent.CopyOnWriteArrayList;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogWriter;
import org.HdrHistogram.SingleWriterRecorder;

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

        private final ThreadLocal<SingleWriterRecorder> recorderTl;
        private final List<SingleWriterRecorder> recorders = new CopyOnWriteArrayList<>();
        private final Timer timer = new Timer();
        private final HistogramLogWriter writer;
        private volatile State state = State.NOT_RECORDING;

        public HistogramLogRecorder(String histogramFilename, int numberOfSignificantValueDigits, int intervalInMs) throws FileNotFoundException
        {
            recorderTl = ThreadLocal.withInitial(() ->
            {
                SingleWriterRecorder singleWriterRecorder = new SingleWriterRecorder(numberOfSignificantValueDigits);
                recorders.add(singleWriterRecorder);
                return singleWriterRecorder;
            });
            writer = new HistogramLogWriter(histogramFilename);
            timer.schedule(new TimerTask()
            {
                private final Histogram collectiveHistogram = new Histogram(numberOfSignificantValueDigits);
                private Histogram intervalHistogram;
                @Override
                public void run()
                {
                    for (SingleWriterRecorder recorder : recorders)
                    {
                        intervalHistogram = recorder.getIntervalHistogram(intervalHistogram, false);
                        collectiveHistogram.add(intervalHistogram);
                    }
                    if (state == State.RECORDING)
                        writer.outputIntervalHistogram(collectiveHistogram);
                    collectiveHistogram.reset();
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
            // Always record values even if state != State.RECORDING, the timer won't write the
            // histogram data on disk, but the histogram code will be jit'ed.
            recorderTl.get().recordValue(value);
        }
    }
}
