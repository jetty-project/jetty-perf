package org.eclipse.jetty.perf.monitoring;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

class AsyncProfilerAllocationMonitor extends AbstractAsyncProfilerMonitor
{
    public static final String DEFAULT_FILENAME = "async-profiler-alloc.html";

    private final String outputFilename;

    public AsyncProfilerAllocationMonitor() throws Exception
    {
        this.outputFilename = DEFAULT_FILENAME;
    }

    public AsyncProfilerAllocationMonitor(String outputFilename) throws Exception
    {
        this.outputFilename = outputFilename;
    }

    public AsyncProfilerAllocationMonitor(String outputFilename, long pid) throws Exception
    {
        super(pid);
        this.outputFilename = outputFilename;
    }

    @Override
    protected Collection<String> extraStartCmdLineArgs()
    {
        return Arrays.asList("-e", "alloc");
    }

    @Override
    protected Collection<String> extraStopCmdLineArgs()
    {
        File file = new File(outputFilename);
        return Arrays.asList("-f", file.getAbsolutePath());
    }
}
