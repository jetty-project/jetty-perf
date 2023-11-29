package org.eclipse.jetty.perf.monitoring.os;

public class LinuxPerfStatMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_FILENAME = "os/perf-stat.log";

    public LinuxPerfStatMonitor()
    {
        this(DEFAULT_FILENAME);
    }

    public LinuxPerfStatMonitor(String filename)
    {
        super(filename, "perf", "stat", "--log-fd", "1", "-p", Long.toString(ProcessHandle.current().pid()));
    }

    @Override
    public void close() throws Exception
    {
        if (process != null)
        {
            // linux perf MUST receive SIGINT or it won't output any information.
            Process kill = new ProcessBuilder("kill", "-INT", Long.toString(process.toHandle().pid()))
                .start();
            kill.waitFor();

            // after kill exited, perf should exist soonish
            process.waitFor();
        }
    }
}
