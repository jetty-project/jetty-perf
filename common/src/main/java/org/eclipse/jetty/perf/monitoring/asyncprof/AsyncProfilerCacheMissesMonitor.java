package org.eclipse.jetty.perf.monitoring.asyncprof;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class AsyncProfilerCacheMissesMonitor extends AbstractAsyncProfilerMonitor
{
    public static final String DEFAULT_FILENAME = "async-profiler-cache-misses.html";

    private final String outputFilename;

    public AsyncProfilerCacheMissesMonitor() throws Exception
    {
        this.outputFilename = DEFAULT_FILENAME;
    }

    public AsyncProfilerCacheMissesMonitor(String outputFilename) throws Exception
    {
        this.outputFilename = outputFilename;
    }

    public AsyncProfilerCacheMissesMonitor(String outputFilename, long pid) throws Exception
    {
        super(pid);
        this.outputFilename = outputFilename;
    }

    @Override
    protected Collection<String> extraStartCmdLineArgs()
    {
        return List.of("-e", "cache-misses");
    }

    @Override
    protected Collection<String> extraStopCmdLineArgs()
    {
        File file = new File(outputFilename);
        return List.of("-f", file.getAbsolutePath());
    }
}
