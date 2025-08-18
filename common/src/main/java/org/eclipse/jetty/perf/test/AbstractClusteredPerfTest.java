package org.eclipse.jetty.perf.test;

import java.io.Closeable;
import java.io.Serializable;
import java.nio.file.Path;

import org.eclipse.jetty.perf.util.SerializableConsumer;
import org.mortbay.jetty.orchestrator.Cluster;

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
