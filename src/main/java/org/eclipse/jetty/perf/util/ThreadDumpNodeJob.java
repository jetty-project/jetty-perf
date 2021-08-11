package org.eclipse.jetty.perf.util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import org.mortbay.jetty.orchestrator.ClusterTools;
import org.mortbay.jetty.orchestrator.NodeJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadDumpNodeJob implements NodeJob
{
    private static final Logger LOG = LoggerFactory.getLogger(ThreadDumpNodeJob.class);

    private static void threadDump(boolean lockedMonitors, boolean lockedSynchronizers)
    {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(lockedMonitors, lockedSynchronizers))
        {
            LOG.info(threadInfo.toString());
        }
    }

    @Override
    public void execute(ClusterTools clusterTools) throws Exception
    {
        threadDump(true, true);
    }
}
