package org.eclipse.jetty.perf.monitoring;

class WindowsMemoryMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "memory.txt";

    public WindowsMemoryMonitor()
    {
        this(DEFAULT_FILENAME, 10);
    }

    public WindowsMemoryMonitor(String filename, int interval)
    {
        super(filename, "powershell", "Get-Counter", "'\\Memory\\*'", "-Continuous", "-SampleInterval", Integer.toString(interval));
    }
}
