package org.eclipse.jetty.perf.monitoring.asyncprof;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class AsyncProfilerCpuMonitor extends AbstractAsyncProfilerMonitor
{
    public static final String DEFAULT_FILENAME = "async-profiler/cpu.html";

    public AsyncProfilerCpuMonitor() throws Exception
    {
        this(DEFAULT_FILENAME);
    }

    public AsyncProfilerCpuMonitor(String outputFilename) throws Exception
    {
        super(outputFilename);
    }

    public AsyncProfilerCpuMonitor(String outputFilename, long pid) throws Exception
    {
        super(pid, outputFilename);
    }

    @Override
    protected Collection<String> extraStartCmdLineArgs()
    {
        return List.of("-e", "cpu");
    }
}
