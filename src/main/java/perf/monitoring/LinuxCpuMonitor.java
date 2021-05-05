package perf.monitoring;

import java.io.File;
import java.io.IOException;

class LinuxCpuMonitor implements Monitor
{
    public static final String DEFAULT_FILENAME = "cpu.txt";

    private final Process process;

    public LinuxCpuMonitor() throws IOException
    {
        this(DEFAULT_FILENAME, 10);
    }

    public LinuxCpuMonitor(String filename, int interval) throws IOException
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
