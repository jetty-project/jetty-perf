package org.eclipse.jetty.perf.monitoring;

class WindowsNetworkMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "network.txt";

    public WindowsNetworkMonitor()
    {
        this(DEFAULT_FILENAME, 10);
    }

    public WindowsNetworkMonitor(String filename, int interval)
    {
        super(filename, "powershell", "Get-Counter", "'\\Network Interface(*)\\*'", "-Continuous", "-SampleInterval", Integer.toString(interval));
    }
}
