package org.eclipse.jetty.perf.monitoring.os;

import java.io.File;

public class LinuxPerfC2cMonitor extends AbstractCommandMonitor
{
    public static final String DEFAULT_RECORD_FILENAME = "os/perf-c2c-record.log";
    public static final String DEFAULT_REPORT_FILENAME = "os/perf-c2c-report.log";

    public LinuxPerfC2cMonitor()
    {
        this(DEFAULT_RECORD_FILENAME);
    }

    public LinuxPerfC2cMonitor(String filename)
    {
        super(filename, "perf", "c2c", "record", "-p", Long.toString(ProcessHandle.current().pid()));
    }

    @Override
    public void close() throws Exception
    {
        if (process != null)
        {
            // linux perf MUST receive SIGINT or it won't output any information.
            Process kill = new ProcessBuilder("kill", "-INT", Long.toString(process.toHandle().pid())).start();
            kill.waitFor();

            // after kill exited, perf should exit soonish
            process.waitFor();

            // output the report
            Process p = new ProcessBuilder("perf", "c2c", "report", "--stdio")
                .redirectErrorStream(true)
                .redirectOutput(new File(DEFAULT_REPORT_FILENAME))
                .start();
            p.waitFor();
        }
    }
}
