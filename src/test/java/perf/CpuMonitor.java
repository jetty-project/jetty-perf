package perf;

import java.io.File;
import java.io.IOException;

public class CpuMonitor implements AutoCloseable
{
    private final Process process;

    public CpuMonitor(String filename) throws IOException
    {
        this(filename, 10);
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
