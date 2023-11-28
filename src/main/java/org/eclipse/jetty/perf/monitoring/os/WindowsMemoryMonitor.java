package org.eclipse.jetty.perf.monitoring.os;

public class WindowsMemoryMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "os\\memory.log";

    public WindowsMemoryMonitor()
    {
        this(DEFAULT_FILENAME, DEFAULT_INTERVAL);
    }

    public WindowsMemoryMonitor(String filename, int interval)
    {
        super(filename, "powershell", "Get-Counter", "'\\Memory\\*'", "-Continuous", "-SampleInterval", Integer.toString(interval));
    }
}
