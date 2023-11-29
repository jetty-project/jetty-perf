package org.eclipse.jetty.perf.monitoring.os;

public class LinuxPerfStatMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "os/perf-stat.log";

    public LinuxPerfStatMonitor()
    {
        this(DEFAULT_FILENAME);
    }

    public LinuxPerfStatMonitor(String filename)
    {
        super(filename, "perf", "stat", "-p", Long.toString(ProcessHandle.current().pid()));
    }
}
