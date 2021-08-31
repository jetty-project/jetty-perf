package org.eclipse.jetty.perf.monitoring.os;

public class WindowsCpuMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "os\\cpu.log";

    public WindowsCpuMonitor()
    {
        this(DEFAULT_FILENAME, 10);
    }

    public WindowsCpuMonitor(String filename, int interval)
    {
        super(filename, "powershell", "Get-Counter", "'\\Processor(*)\\% Processor Time'", "-Continuous", "-SampleInterval", Integer.toString(interval));
    }
}
