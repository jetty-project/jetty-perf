package org.eclipse.jetty.perf.monitoring.sjk;

public class SjkTtopMonitor extends AbstractSjkMonitor
{
    public static final String DEFAULT_FILENAME = "jvm/ttop.log";

    public SjkTtopMonitor()
    {
        this(DEFAULT_FILENAME, DEFAULT_INTERVAL);
    }

    public SjkTtopMonitor(String filename, int interval)
    {
        super(filename, "ttop", "-p", Long.toString(ProcessHandle.current().pid()), "-ri", interval + "s");
    }
}
