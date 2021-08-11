package org.eclipse.jetty.perf.monitoring;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Timer;
import java.util.TimerTask;
import javax.management.MBeanServer;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DumpHeapRepeatedlyMonitor implements Monitor
{
    private static final Logger LOG = LoggerFactory.getLogger(DumpHeapRepeatedlyMonitor.class);

    private final Timer timer;

    public DumpHeapRepeatedlyMonitor(String dirname, boolean live, long delay) throws IOException
    {
        new File(dirname).mkdirs();
        this.timer = new Timer();
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(server, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
        timer.schedule(new TimerTask() {
            int count = 0;
            @Override
            public void run()
            {
                File filePath = new File(dirname, "heap." + (count++) + ".hprof");
                try
                {
                    mxBean.dumpHeap(filePath.getPath(), live);
                }
                catch (IOException e)
                {
                    LOG.error("Error dumping heap " + filePath, e);
                }
            }
        }, delay, delay);
    }

    @Override
    public void close() throws Exception
    {
        timer.cancel();
    }
}
