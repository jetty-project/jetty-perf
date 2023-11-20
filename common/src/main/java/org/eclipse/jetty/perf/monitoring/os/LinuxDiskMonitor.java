package org.eclipse.jetty.perf.monitoring.os;

public class LinuxDiskMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "os/disk.log";

    public LinuxDiskMonitor()
    {
        this(DEFAULT_FILENAME, DEFAULT_INTERVAL);
    }

    public LinuxDiskMonitor(String filename, int interval)
    {
        super(filename, "iostat", "-y", "-x", Integer.toString(interval));
    }
}
