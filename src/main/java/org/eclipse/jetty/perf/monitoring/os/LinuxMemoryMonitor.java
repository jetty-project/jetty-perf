package org.eclipse.jetty.perf.monitoring.os;

public class LinuxMemoryMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "os/memory.log";

    public LinuxMemoryMonitor()
    {
        this(DEFAULT_FILENAME, DEFAULT_INTERVAL);
    }

    public LinuxMemoryMonitor(String filename, int interval)
    {
        super(filename, "vmstat", "-w", "-S", "m", Integer.toString(interval));
    }
}
