package perf.monitoring;

import java.io.File;
import java.io.IOException;

class NetworkMonitor implements AutoCloseable
{
    public static final String DEFAULT_FILENAME = "network.txt";

    private final Process process;

    public NetworkMonitor() throws IOException
    {
        this(DEFAULT_FILENAME, 10);
    }

    public NetworkMonitor(String filename, int interval) throws IOException
    {
        process = new ProcessBuilder("sar", "-n", "DEV", "-n", "EDEV", "" + interval)
            .redirectErrorStream(true)
            .redirectOutput(new File(filename))
            .start();
    }

    @Override
    public void close() throws Exception
    {
        process.destroy();
        process.waitFor();
    }
}
