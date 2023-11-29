package org.eclipse.jetty.perf.monitoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.eclipse.jetty.perf.monitoring.os.LinuxCpuMonitor;
import org.eclipse.jetty.perf.monitoring.os.LinuxDiskMonitor;
import org.eclipse.jetty.perf.monitoring.os.LinuxMemoryMonitor;
import org.eclipse.jetty.perf.monitoring.os.LinuxNetworkMonitor;
import org.eclipse.jetty.perf.monitoring.os.LinuxPerfStatMonitor;
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
        CMDLINE_DISK,

        // Only one kind of async profiling can be enabled at a time.
        ASYNC_PROF_CPU,
        ASYNC_PROF_ALLOCATION,
        ASYNC_PROF_LOCK,

        PERF_STAT,

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

    public static List<ConfigurableMonitor.Item> parseConfigurableMonitorItems(String cmd)
    {
        return Arrays.stream(cmd.split(","))
            .map(String::trim)
            .map(s -> {
                try
                {
                    return ConfigurableMonitor.Item.valueOf(s);
                }
                catch (IllegalArgumentException e)
                {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
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
            case CMDLINE_DISK:
                if (osName.contains("linux"))
                    return new LinuxDiskMonitor();
                return null;
            case ASYNC_PROF_CPU:
                if (osName.contains("linux"))
                    return new AsyncProfilerCpuMonitor();
                return null;
            case ASYNC_PROF_ALLOCATION:
                if (osName.contains("linux"))
                    return new AsyncProfilerAllocationMonitor();
                return null;
            case ASYNC_PROF_LOCK:
                if (osName.contains("linux"))
                    return new AsyncProfilerLockMonitor();
                return null;
            case PERF_STAT:
                if (osName.contains("linux"))
                    return new LinuxPerfStatMonitor();
                return null;
            case JHICCUP:
                return new JHiccupMonitor();
            case GC_LOGS:
                return null;
            default:
                throw new AssertionError("Unknown monitor item : " +item);
        }
    }
}
