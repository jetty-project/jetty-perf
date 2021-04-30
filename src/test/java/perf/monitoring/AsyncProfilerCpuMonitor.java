package perf.monitoring;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

class AsyncProfilerCpuMonitor extends AbstractAsyncProfilerMonitor
{
    public static final String DEFAULT_FILENAME = "async-profiler-cpu.html";

    private final String outputFilename;

    public AsyncProfilerCpuMonitor() throws Exception
    {
        this.outputFilename = DEFAULT_FILENAME;
    }

    public AsyncProfilerCpuMonitor(String outputFilename) throws Exception
    {
        this.outputFilename = outputFilename;
    }

    public AsyncProfilerCpuMonitor(String outputFilename, long pid) throws Exception
    {
        super(pid);
        this.outputFilename = outputFilename;
    }

    @Override
    protected Collection<String> extraStartCmdLineArgs()
    {
        return Collections.emptyList();
    }

    @Override
    protected Collection<String> extraStopCmdLineArgs()
    {
        File file = new File(outputFilename);
        return Arrays.asList("-f", file.getAbsolutePath());
    }
}
