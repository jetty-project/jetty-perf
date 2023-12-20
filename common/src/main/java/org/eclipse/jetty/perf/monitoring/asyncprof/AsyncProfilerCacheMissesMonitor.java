package org.eclipse.jetty.perf.monitoring.asyncprof;

import java.util.Collection;
import java.util.List;

public class AsyncProfilerCacheMissesMonitor extends AbstractAsyncProfilerMonitor
{
    public static final String DEFAULT_FILENAME = "async-profiler/cache-misses.html";

    public AsyncProfilerCacheMissesMonitor() throws Exception
    {
        this(DEFAULT_FILENAME);
    }

    public AsyncProfilerCacheMissesMonitor(String outputFilename) throws Exception
    {
        super(outputFilename);
    }

    public AsyncProfilerCacheMissesMonitor(String outputFilename, long pid) throws Exception
    {
        super(pid, outputFilename);
    }

    @Override
    protected Collection<String> extraStartCmdLineArgs()
    {
        return List.of("-e", "cache-misses");
    }
}
