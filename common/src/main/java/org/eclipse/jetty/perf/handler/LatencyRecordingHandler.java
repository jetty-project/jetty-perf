package org.eclipse.jetty.perf.handler;

import org.eclipse.jetty.perf.util.LatencyRecorder;
import org.eclipse.jetty.server.handler.AbstractLatencyRecordingHandler;

/**
 * Like it is done from 12.0.0.beta0
 */
public class LatencyRecordingHandler extends AbstractLatencyRecordingHandler
{
    private final LatencyRecorder recorder;

    public LatencyRecordingHandler(LatencyRecorder recorder)
    {
        this.recorder = recorder;
    }

    @Override
    protected void onRequestComplete(long durationInNs)
    {
        recorder.recordValue(durationInNs);
    }
}
