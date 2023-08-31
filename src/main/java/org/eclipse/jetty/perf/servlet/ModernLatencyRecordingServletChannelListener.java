package org.eclipse.jetty.perf.servlet;

import java.io.FileNotFoundException;

import jakarta.servlet.http.HttpServlet;
import org.eclipse.jetty.perf.util.HistogramLogRecorder;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;

public abstract class ModernLatencyRecordingServletChannelListener extends HttpServlet implements HttpChannel.Listener
{
    private final HistogramLogRecorder recorder;

    public ModernLatencyRecordingServletChannelListener() throws Exception
    {
        this("perf.hlog");
    }

    public ModernLatencyRecordingServletChannelListener(String histogramFilename) throws FileNotFoundException
    {
        this(new HistogramLogRecorder(histogramFilename, 3, 1000));
    }

    public ModernLatencyRecordingServletChannelListener(HistogramLogRecorder recorder)
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

    @Override
    public void onComplete(Request request)
    {
        long responseTime = System.nanoTime() - request.getBeginNanoTime();
        recorder.recordValue(responseTime);
    }
}
