package perf.monitoring;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;

import com.sun.management.HotSpotDiagnosticMXBean;

class DumpHeapOnCloseMonitor implements Monitor
{
    public static final String DEFAULT_FILENAME = "heap.hprof";
    private final String filename;
    private final boolean live;

    public DumpHeapOnCloseMonitor()
    {
        this(DEFAULT_FILENAME, true);
    }

    public DumpHeapOnCloseMonitor(String filename, boolean live)
    {
        this.filename = filename;
        this.live = live;
    }

    @Override
    public void close() throws Exception
    {
        dumpHeap(filename, live);
    }

    public static void dumpHeap(String filePath, boolean live) throws IOException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(server, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
        mxBean.dumpHeap(filePath, live);
    }
}
