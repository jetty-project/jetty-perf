package org.eclipse.jetty.perf.monitoring;

class LinuxCpuMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "cpu.txt";

    public LinuxCpuMonitor()
    {
        this(DEFAULT_FILENAME, 10);
    }

    public LinuxCpuMonitor(String filename, int interval)
    {
        super(filename, "mpstat", "-P", "ALL", Integer.toString(interval));
    }
}
