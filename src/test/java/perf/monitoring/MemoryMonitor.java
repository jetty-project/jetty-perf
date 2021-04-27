package perf.monitoring;

import java.io.File;
import java.io.IOException;

class MemoryMonitor implements AutoCloseable
{
    public static final String DEFAULT_FILENAME = "memory.txt";

    private final Process process;

    public MemoryMonitor() throws IOException
    {
        this(DEFAULT_FILENAME, 10);
    }

    public MemoryMonitor(String filename, int interval) throws IOException
    {
        process = new ProcessBuilder("free", "-h", "-s", "" + interval)
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
