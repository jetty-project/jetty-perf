package org.eclipse.jetty.perf.monitoring;

class LinuxNetworkMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "network.txt";

    public LinuxNetworkMonitor()
    {
        this(DEFAULT_FILENAME, 10);
    }

    public LinuxNetworkMonitor(String filename, int interval)
    {
        super(filename, "sar", "-n", "DEV", "-n", "EDEV", Integer.toString(interval));
    }
}
