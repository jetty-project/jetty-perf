package org.eclipse.jetty.perf.monitoring;

import java.io.FileNotFoundException;

class JHiccupMonitor implements Monitor
{
    public static final String DEFAULT_FILENAME = "jhiccup.hlog";

    private final HiccupMeter hiccupMeter;

    public JHiccupMonitor() throws FileNotFoundException
    {
        hiccupMeter = new HiccupMeter(new String[]{"-i", "1000", "-a"}, DEFAULT_FILENAME);
        hiccupMeter.start();
    }

    @Override
    public void close() throws Exception
    {
        hiccupMeter.interrupt();
        hiccupMeter.join();
    }
}
