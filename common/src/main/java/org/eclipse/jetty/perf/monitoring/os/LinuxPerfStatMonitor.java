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
        super("/dev/null", "perf", "stat", "-p", Long.toString(ProcessHandle.current().pid()), "-o", filename);
    }

    @Override
    public void close() throws Exception
    {
        // no-op; the end of the current process will stop this monitor.
    }
}
