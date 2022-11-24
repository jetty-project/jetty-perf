package org.eclipse.jetty.perf.monitoring.os;

public class LinuxNetworkMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "os/network.log";

    public LinuxNetworkMonitor()
    {
        this(DEFAULT_FILENAME, 10);
    }

    public LinuxNetworkMonitor(String filename, int interval)
    {
        super(filename, "sar", "-n", "DEV", "-n", "EDEV", Integer.toString(interval));
    }
}
