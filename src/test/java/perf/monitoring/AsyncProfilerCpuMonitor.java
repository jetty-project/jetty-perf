package perf.monitoring;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

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
        // Workaround for JDK 16 bug for when ZGC is enabled.
        // See: https://github.com/jvm-profiling-tools/async-profiler/issues/422
        return Arrays.asList("--safe-mode", "64");
    }

    @Override
    protected Collection<String> extraStopCmdLineArgs()
    {
        File file = new File(outputFilename);
        return Arrays.asList("-f", file.getAbsolutePath());
    }
}
