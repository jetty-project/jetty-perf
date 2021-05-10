package perf.monitoring;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractCommandMonitor implements Monitor
{
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCommandMonitor.class);

    private final Process process;

    protected AbstractCommandMonitor(String outputfilename, String... command)
    {
        Process process = null;
        try
        {
            process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(new File(outputfilename))
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
