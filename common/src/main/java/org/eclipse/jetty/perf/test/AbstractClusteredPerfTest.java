package org.eclipse.jetty.perf.test;

import java.io.Closeable;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;
import org.eclipse.jetty.perf.util.Recorder;
import org.eclipse.jetty.perf.util.SerializableConsumer;
import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.ClusterTools;
import org.mortbay.jetty.orchestrator.NodeArray;
import org.mortbay.jetty.orchestrator.NodeArrayFuture;
import org.mortbay.jetty.orchestrator.NodeJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.perf.util.ReportUtil.generateReport;

public abstract class AbstractClusteredPerfTest implements Serializable, Closeable
{
    protected final PerfTestParams perfTestParams;
    protected final String reportRootPath; // java.nio.Path isn't serializable, so we must use a String.
    protected final SerializableConsumer<PerfTestParams> perfTestParamsCustomizer; // the customizer MUST be run on the target node, not by the test!
    protected transient Cluster cluster; // not serializable, but there is no need to access this field from remote lambdas.

    protected AbstractClusteredPerfTest(String testName, Path reportRootPath, PerfTestParams perfTestParams, SerializableConsumer<PerfTestParams> perfTestParamsCustomizer) throws Exception
    {
        this.perfTestParams = perfTestParams;
        this.reportRootPath = reportRootPath.toString();
        this.perfTestParamsCustomizer = perfTestParamsCustomizer;
        this.cluster = perfTestParams.buildCluster(testName);
    }

    @Override
    public final void close()
    {
        if (cluster != null)
        {
            cluster.close();
            cluster = null;
        }
    }

    protected abstract void execute() throws Exception;
}
