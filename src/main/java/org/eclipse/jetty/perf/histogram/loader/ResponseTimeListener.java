package org.eclipse.jetty.perf.histogram.loader;

import org.eclipse.jetty.perf.util.LatencyRecorder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

public class ResponseTimeListener implements Resource.NodeListener, LoadGenerator.CompleteListener
{
    private final LatencyRecorder recorder;

    public ResponseTimeListener(LatencyRecorder latencyRecorder)
    {
        this.recorder = latencyRecorder;
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
