package org.eclipse.jetty.perf.monitoring.asyncprof;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import one.convert.Arguments;
import one.convert.JfrToFlame;
import one.convert.JfrToHeatmap;
import one.profiler.AsyncProfiler;
import org.eclipse.jetty.perf.monitoring.Monitor;

public class JfrAsyncProfilerMonitor implements Monitor
{
    private final AsyncProfiler profiler;
    private final Path outputPath;

    public JfrAsyncProfilerMonitor() throws Exception
    {
        outputPath = Path.of("async-profiler/async-profiler.jfr");
        try
        {
            Files.createDirectories(outputPath.getParent());
        }
        catch (FileAlreadyExistsException e)
        {
            // this is fine
        }
        profiler = AsyncProfiler.getInstance();
        profiler.execute("start,jfr,features=comptask,event=cpu,file=" + outputPath.toAbsolutePath());
    }

    @Override
    public void close() throws Exception
    {
        profiler.execute("stop");
        JfrToHeatmap.convert(outputPath.toString(), outputPath.getParent().resolve("heatmap.html").toString(), new Arguments());
        JfrToFlame.convert(outputPath.toString(), outputPath.getParent().resolve("cpu.html").toString(), new Arguments());
    }
}
