package perf.monitoring;

import java.io.File;
import java.io.IOException;

class CpuMonitor implements AutoCloseable
{
    public static final String DEFAULT_FILENAME = "cpu.txt";

    private final Process process;

    public CpuMonitor() throws IOException
    {
        this(DEFAULT_FILENAME, 10);
    }

    public CpuMonitor(String filename, int interval) throws IOException
    {
        process = new ProcessBuilder("mpstat", "-P", "ALL", "" + interval)
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
