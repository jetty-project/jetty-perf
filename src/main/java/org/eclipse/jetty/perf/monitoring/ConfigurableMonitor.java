package org.eclipse.jetty.perf.monitoring;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import org.eclipse.jetty.perf.monitoring.os.LinuxCpuMonitor;
import org.eclipse.jetty.perf.monitoring.os.LinuxMemoryMonitor;
import org.eclipse.jetty.perf.monitoring.os.LinuxNetworkMonitor;
import org.eclipse.jetty.perf.monitoring.os.WindowsCpuMonitor;
import org.eclipse.jetty.perf.monitoring.os.WindowsMemoryMonitor;
import org.eclipse.jetty.perf.monitoring.os.WindowsNetworkMonitor;
import org.eclipse.jetty.perf.util.IOUtil;

public class ConfigurableMonitor implements Monitor
{
    public enum Item
    {
        CMDLINE_CPU,
        CMDLINE_MEMORY,
        CMDLINE_NETWORK,
        ASYNC_PROF_CPU,
        ASYNC_PROF_ALLOCATION,
        JHICCUP,
        GC_LOGS,
    }

    private final List<Monitor> monitors = new ArrayList<>();

    public ConfigurableMonitor(EnumSet<Item> items) throws Exception
    {
        for (Item item : items)
        {
            Monitor monitor = monitorOf(item);
            if (monitor != null)
                monitors.add(monitor);
        }
    }

    @Override
    public void close()
    {
        monitors.forEach(IOUtil::close);
    }

    private static Monitor monitorOf(Item item) throws Exception
    {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        switch (item)
        {
            case CMDLINE_CPU:
                if (osName.contains("linux"))
                    return new LinuxCpuMonitor();
                if (osName.contains("windows"))
                    return new WindowsCpuMonitor();
                return null;
            case CMDLINE_MEMORY:
                if (osName.contains("linux"))
                    return new LinuxMemoryMonitor();
                if (osName.contains("windows"))
                    return new WindowsMemoryMonitor();
                return null;
            case CMDLINE_NETWORK:
                if (osName.contains("linux"))
                    return new LinuxNetworkMonitor();
                if (osName.contains("windows"))
                    return new WindowsNetworkMonitor();
                return null;
            case ASYNC_PROF_CPU:
                if (osName.contains("linux"))
                    return new AsyncProfilerCpuMonitor();
                return null;
            case ASYNC_PROF_ALLOCATION:
                if (osName.contains("linux"))
                    return new AsyncProfilerAllocationMonitor();
                return null;
            case JHICCUP:
                return new JHiccupMonitor();
            default:
                throw new AssertionError("Unknown monitor item : " +item);
        }
    }
}
