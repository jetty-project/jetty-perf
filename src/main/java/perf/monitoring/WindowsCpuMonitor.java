package perf.monitoring;

class WindowsCpuMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "cpu.txt";

    public WindowsCpuMonitor()
    {
        this(DEFAULT_FILENAME, 10);
    }

    public WindowsCpuMonitor(String filename, int interval)
    {
        super(filename, "powershell", "Get-Counter", "'\\Processor(*)\\% Processor Time'", "-Continuous", "-SampleInterval", Integer.toString(interval));
    }
}
