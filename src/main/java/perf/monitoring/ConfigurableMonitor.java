package perf.monitoring;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import perf.util.IOUtil;

public class ConfigurableMonitor implements Monitor
{
    public enum Item
    {
        CMDLINE_CPU,
        CMDLINE_MEMORY,
        CMDLINE_NETWORK,
        APROF_CPU,
        HEAP_DUMP_ON_CLOSE,
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

    public static List<String> defaultFilenamesOf(EnumSet<Item> items)
    {
        return items.stream().map(ConfigurableMonitor::defaultFilenameOf).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static String defaultFilenameOf(Item item)
    {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"))
            return null;
        switch (item)
        {
            case CMDLINE_CPU:
                return LinuxCpuMonitor.DEFAULT_FILENAME;
            case CMDLINE_MEMORY:
                return LinuxMemoryMonitor.DEFAULT_FILENAME;
            case CMDLINE_NETWORK:
                return LinuxNetworkMonitor.DEFAULT_FILENAME;
            case APROF_CPU:
                return AsyncProfilerCpuMonitor.DEFAULT_FILENAME;
            case HEAP_DUMP_ON_CLOSE:
                return DumpHeapOnCloseMonitor.DEFAULT_FILENAME;
            default:
                throw new AssertionError("Unknown monitor item : " +item);
        }
    }

    private static Monitor monitorOf(Item item) throws Exception
    {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"))
            return null;
        switch (item)
        {
            case CMDLINE_CPU:
                return new LinuxCpuMonitor();
            case CMDLINE_MEMORY:
                return new LinuxMemoryMonitor();
            case CMDLINE_NETWORK:
                return new LinuxNetworkMonitor();
            case APROF_CPU:
                return new AsyncProfilerCpuMonitor();
            case HEAP_DUMP_ON_CLOSE:
                return new DumpHeapOnCloseMonitor();
            default:
                throw new AssertionError("Unknown monitor item : " +item);
        }
    }
}
