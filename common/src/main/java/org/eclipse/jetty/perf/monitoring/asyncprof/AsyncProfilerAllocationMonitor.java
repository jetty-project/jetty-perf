package org.eclipse.jetty.perf.monitoring.asyncprof;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class AsyncProfilerAllocationMonitor extends AbstractAsyncProfilerMonitor
{
    public static final String DEFAULT_FILENAME = "async-profiler/alloc.html";

    public AsyncProfilerAllocationMonitor() throws Exception
    {
        this(DEFAULT_FILENAME);
    }

    public AsyncProfilerAllocationMonitor(String outputFilename) throws Exception
    {
        super(outputFilename);
    }

    public AsyncProfilerAllocationMonitor(String outputFilename, long pid) throws Exception
    {
        super(pid, outputFilename);
    }

    @Override
    protected Collection<String> extraStartCmdLineArgs()
    {
        return List.of("-e", "alloc");
    }
}
