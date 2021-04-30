package perf.monitoring;

import java.io.File;
import java.io.IOException;

class LinuxMemoryMonitor implements Monitor
{
    public static final String DEFAULT_FILENAME = "memory.txt";

    private final Process process;

    public LinuxMemoryMonitor() throws IOException
    {
        this(DEFAULT_FILENAME, 10);
    }

    public LinuxMemoryMonitor(String filename, int interval) throws IOException
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
