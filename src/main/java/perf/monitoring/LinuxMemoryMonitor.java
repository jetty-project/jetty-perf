package perf.monitoring;

class LinuxMemoryMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "memory.txt";

    public LinuxMemoryMonitor()
    {
        this(DEFAULT_FILENAME, 10);
    }

    public LinuxMemoryMonitor(String filename, int interval)
    {
        super(filename, "free", "-h", "-s", Integer.toString(interval));
    }
}
