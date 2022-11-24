package org.eclipse.jetty.perf.monitoring.os;

public class LinuxCpuMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "os/cpu.log";

    public LinuxCpuMonitor()
    {
        this(DEFAULT_FILENAME, DEFAULT_INTERVAL);
    }

    public LinuxCpuMonitor(String filename, int interval)
    {
        super(filename, "mpstat", "-P", "ALL", Integer.toString(interval));
    }
}
