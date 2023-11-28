package org.eclipse.jetty.perf.monitoring.os;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.eclipse.jetty.perf.monitoring.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractCommandMonitor implements Monitor
{
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCommandMonitor.class);

    public static final int DEFAULT_INTERVAL = 5;

    private final Process process;

    protected AbstractCommandMonitor(String outputfilename, String... command)
    {
        Process process = null;
        try
        {
            File outputFile = new File(outputfilename);
            if (!outputFile.getParentFile().isDirectory() && !outputFile.getParentFile().mkdirs())
                throw new IOException("Cannot create folder for output file " + outputFile.getAbsolutePath() + " of command " + Arrays.toString(command));
            process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
                .start();
        }
        catch (IOException e)
        {
            LOG.warn("Error starting monitoring command: {}", e.getMessage());
        }
        this.process = process;
    }

    @Override
    public void close() throws Exception
    {
        if (process != null)
        {
            process.destroy();
            process.waitFor();
        }
    }
}
