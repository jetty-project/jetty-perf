package org.eclipse.jetty.perf.monitoring.asyncprof;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import one.profiler.AsyncProfiler;
import org.eclipse.jetty.perf.monitoring.Monitor;

abstract class AbstractAsyncProfilerMonitor implements Monitor
{
    private final AsyncProfiler profiler;
    private final Path outputPath;

    protected AbstractAsyncProfilerMonitor(String eventName) throws Exception
    {
        outputPath = Path.of("async-profiler/" + eventName + ".html");
        try
        {
            Files.createDirectories(outputPath.getParent());
        }
        catch (FileAlreadyExistsException e)
        {
            // this is fine
        }
        profiler = AsyncProfiler.getInstance();
        profiler.execute("start,event=" + eventName);
    }

    @Override
    public void close() throws Exception
    {
        profiler.execute("stop,file=" + outputPath.toAbsolutePath());
    }
}
