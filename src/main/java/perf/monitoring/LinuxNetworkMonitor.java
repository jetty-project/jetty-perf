package perf.monitoring;

import java.io.File;
import java.io.IOException;

class LinuxNetworkMonitor implements Monitor
{
    public static final String DEFAULT_FILENAME = "network.txt";

    private final Process process;

    public LinuxNetworkMonitor() throws IOException
    {
        this(DEFAULT_FILENAME, 10);
    }

    public LinuxNetworkMonitor(String filename, int interval) throws IOException
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
