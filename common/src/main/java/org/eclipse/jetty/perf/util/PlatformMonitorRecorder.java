package org.eclipse.jetty.perf.util;

import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.jetty.toolchain.perf.PlatformMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlatformMonitorRecorder implements Recorder
{
    private static final Logger LOG = LoggerFactory.getLogger(PlatformMonitorRecorder.class);

    public static final String FILENAME = "PlatformMonitor.txt";
    public static final String PLACEHOLDER = "##HISTOGRAM##";

    private final PlatformMonitor pm = new PlatformMonitor();
    private PlatformMonitor.Start start;

    @Override
    public void startRecording()
    {
        start = pm.start();
    }

    @Override
    public void stopRecording()
    {
        PlatformMonitor.Stop stop = pm.stop();
        try
        {
            try (PrintWriter printWriter = new PrintWriter(FILENAME))
            {
                printWriter.println(start);
                printWriter.println(PLACEHOLDER);
                printWriter.println(stop);
            }
        }
        catch (IOException ioe)
        {
            LOG.error("Error writing PlatformMonitor report", ioe);
        }
    }
}
