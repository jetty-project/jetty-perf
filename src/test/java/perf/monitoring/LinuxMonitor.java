package perf.monitoring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.mortbay.jetty.orchestrator.util.IOUtil;

public class LinuxMonitor implements AutoCloseable
{
    public static final List<String> DEFAULT_FILENAMES = Arrays.asList(CpuMonitor.DEFAULT_FILENAME, NetworkMonitor.DEFAULT_FILENAME, MemoryMonitor.DEFAULT_FILENAME);

    private final List<AutoCloseable> monitors = new ArrayList<>();

    public LinuxMonitor() throws IOException
    {
        monitors.add(new CpuMonitor());
        monitors.add(new NetworkMonitor());
        monitors.add(new MemoryMonitor());
    }

    @Override
    public void close()
    {
        monitors.forEach(IOUtil::close);
    }
}
