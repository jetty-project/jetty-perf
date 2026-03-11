package org.eclipse.jetty.perf.monitoring.jmx;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.jetty.perf.monitoring.Monitor;

public class JitCompilationMonitor implements Monitor
{
    public static final String DEFAULT_FILENAME = "jmx/jit-compilation-time.log";

    private final Timer timer = new Timer();
    private final PrintWriter writer;

    public JitCompilationMonitor() throws IOException
    {
        this(DEFAULT_FILENAME, 1000);
    }

    public JitCompilationMonitor(String outputFilename, int intervalInMs) throws IOException
    {
        CompilationMXBean compilationMXBean = ManagementFactory.getCompilationMXBean();
        try
        {
            Files.createDirectories(Path.of(outputFilename).getParent());
        }
        catch (FileAlreadyExistsException e)
        {
            // this is fine
        }
        writer = new PrintWriter(outputFilename);
        timer.schedule(new TimerTask()
        {
            long previousCompilationTime = compilationMXBean.getTotalCompilationTime();
            int counter = 0;

            @Override
            public void run()
            {
                long currentCompilationTime = compilationMXBean.getTotalCompilationTime();
                long delta = currentCompilationTime - previousCompilationTime;
                previousCompilationTime = currentCompilationTime;
                writer.println((counter++) + ": " + delta + "ms");
            }
        }, intervalInMs, intervalInMs);
    }

    @Override
    public void close() throws Exception
    {
        timer.cancel();
        writer.close();
    }
}
