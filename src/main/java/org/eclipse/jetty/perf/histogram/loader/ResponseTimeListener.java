package org.eclipse.jetty.perf.histogram.loader;

import java.io.FileNotFoundException;

import org.eclipse.jetty.perf.util.HistogramLogRecorder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

public class ResponseTimeListener implements Resource.NodeListener, LoadGenerator.CompleteListener
{
    private final HistogramLogRecorder recorder;

    public ResponseTimeListener() throws FileNotFoundException
    {
        this("perf.hlog");
    }

    public ResponseTimeListener(String histogramFilename) throws FileNotFoundException
    {
        this.recorder = new HistogramLogRecorder(histogramFilename, 3, 1000);
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
    public void onResourceNode(Resource.Info info)
    {
        long responseTime = info.getResponseTime() - info.getRequestTime();
        recorder.recordValue(responseTime);
    }

    @Override
    public void onComplete(LoadGenerator generator)
    {
       stopRecording();
    }
}
