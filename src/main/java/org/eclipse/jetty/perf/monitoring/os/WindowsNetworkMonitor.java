package org.eclipse.jetty.perf.monitoring.os;

public class WindowsNetworkMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "os\\network.log";

    public WindowsNetworkMonitor()
    {
        this(DEFAULT_FILENAME, 10);
    }

    public WindowsNetworkMonitor(String filename, int interval)
    {
        super(filename, "powershell", "Get-Counter", "'\\Network Interface(*)\\*'", "-Continuous", "-SampleInterval", Integer.toString(interval));
    }
}
