package org.eclipse.jetty.perf.monitoring.asyncprof;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class AsyncProfilerLockMonitor extends AbstractAsyncProfilerMonitor
{
    public static final String DEFAULT_FILENAME = "async-profiler/lock.html";

    public AsyncProfilerLockMonitor() throws Exception
    {
        this(DEFAULT_FILENAME);
    }

    public AsyncProfilerLockMonitor(String outputFilename) throws Exception
    {
        super(outputFilename);
    }

    public AsyncProfilerLockMonitor(String outputFilename, long pid) throws Exception
    {
        super(pid, outputFilename);
    }

    @Override
    protected Collection<String> extraStartCmdLineArgs()
    {
        return List.of("-e", "lock");
    }
}
