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
    private static final Logger LOG = LoggerFactory.getLogger(AbstractClusteredPerfTest.class);

    private final PerfTestParams perfTestParams;
    private final String reportRootPath; // java.nio.Path isn't serializable, so we must use a String.
    private final SerializableConsumer<PerfTestParams> perfTestParamsCustomizer; // the customizer MUST be run on the target node, not by the test!
    private transient Cluster cluster; // not serializable, but there is no need to access this field from remote lambdas.

    protected AbstractClusteredPerfTest(String testName, Path reportRootPath, PerfTestParams perfTestParams, SerializableConsumer<PerfTestParams> perfTestParamsCustomizer) throws Exception
    {
        this.perfTestParams = perfTestParams;
        this.reportRootPath = reportRootPath.toString();
        this.perfTestParamsCustomizer = perfTestParamsCustomizer;
        this.cluster = perfTestParams.buildCluster(testName);
    }

    @Override
    public void close()
    {
        if (cluster != null)
        {
            cluster.close();
            cluster = null;
        }
    }

    public void execute() throws Exception
    {
        NodeArray serverArray = cluster.nodeArray("server");
        NodeArray loadersArray = cluster.nodeArray("loaders");
        NodeArray probeArray = cluster.nodeArray("probe");

        NodeJob logSysInfo = tools -> LOG.info("{} '{}/{}': running JVM version '{}'",
            tools.getGlobalNodeId().getHostname(),
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            System.getProperty("java.vm.version"));
        List<NodeArrayFuture> futures = List.of(
            serverArray.executeOnAll(logSysInfo),
            loadersArray.executeOnAll(logSysInfo),
            probeArray.executeOnAll(logSysInfo)
        );
        for (NodeArrayFuture future : futures)
        {
            future.get(30, TimeUnit.SECONDS);
        }

        LOG.info("Starting the server...");
        serverArray.executeOnAll(tools ->
        {
            perfTestParamsCustomizer.accept(perfTestParams);
            startServer(perfTestParams, tools);
        }).get(30, TimeUnit.SECONDS);
        LOG.info("Starting the loaders...");
        loadersArray.executeOnAll(tools ->
        {
            perfTestParamsCustomizer.accept(perfTestParams);
            runLoadGenerator(perfTestParams, tools);
        }).get(30, TimeUnit.SECONDS);
        LOG.info("Starting the probe...");
        probeArray.executeOnAll(tools ->
        {
            perfTestParamsCustomizer.accept(perfTestParams);
            runProbeGenerator(perfTestParams, tools);
        }).get(30, TimeUnit.SECONDS);

        LOG.info("Warming up {}s ...", perfTestParams.getWarmupDuration().toSeconds());
        Thread.sleep(perfTestParams.getWarmupDuration().toMillis());

        LOG.info("Running {}s ...", perfTestParams.getRunDuration().toSeconds());
        long before = System.nanoTime();

        NodeJob recordingJob = tools ->
        {
            try (ConfigurableMonitor ignore = new ConfigurableMonitor(perfTestParams.getMonitoredItems()))
            {
                @SuppressWarnings("unchecked")
                List<Recorder> recorders = (List<Recorder>)tools.nodeEnvironment().get(Recorder.class.getName());

                recorders.forEach(Recorder::startRecording);
                tools.barrier("run-start-barrier", perfTestParams.getParticipantCount()).await();
                tools.barrier("run-end-barrier", perfTestParams.getParticipantCount()).await();
                recorders.forEach(Recorder::stopRecording);

                CompletableFuture<?> cf = (CompletableFuture<?>)tools.nodeEnvironment().get(CompletableFuture.class.getName());
                cf.get();
            }
            catch (Throwable x)
            {
                LOG.error("Caught exception in job", x);
                throw x;
            }
        };
        NodeArrayFuture serverFuture = serverArray.executeOnAll(recordingJob);
        NodeArrayFuture loadersFuture = loadersArray.executeOnAll(recordingJob);
        NodeArrayFuture probeFuture = probeArray.executeOnAll(recordingJob);

        try
        {
            try
            {
                LOG.info("  Signalling all participants to start recording...");
                cluster.tools().barrier("run-start-barrier", perfTestParams.getParticipantCount()).await(30, TimeUnit.SECONDS);
                LOG.info("  Waiting for the duration of the run...");
                Thread.sleep(perfTestParams.getRunDuration().toMillis());
                LOG.info("  Signalling all participants to stop recording...");
                cluster.tools().barrier("run-end-barrier", perfTestParams.getParticipantCount()).await(30, TimeUnit.SECONDS);
                LOG.info("  Signalled all participants to stop recording");
            }
            finally
            {
                waitForFutures(30, TimeUnit.SECONDS, serverFuture, loadersFuture, probeFuture);
            }

            LOG.info("Stopping the server...");
            serverArray.executeOnAll((tools) ->
            {
                perfTestParamsCustomizer.accept(perfTestParams);
                stopServer(perfTestParams, tools);
            }).get(30, TimeUnit.SECONDS);

            LOG.info("Generating report...");
            generateReport(Path.of(reportRootPath), perfTestParams.getNodeArrayIds(), cluster);

            long after = System.nanoTime();
            LOG.info("Done; elapsed={} ms", TimeUnit.NANOSECONDS.toMillis(after - before));
        }
        catch (Exception e)
        {
            StringBuilder msg = new StringBuilder("Error stopping jobs");
            try
            {
                LOG.info("Downloading artefacts before rethrowing...");
                generateReport(Path.of(reportRootPath), perfTestParams.getNodeArrayIds(), cluster);

                LOG.info("Dumping threads of pending jobs before rethrowing...");
                NodeJob dump = (tools) ->
                {
                    String nodeId = tools.getGlobalNodeId().getNodeId();
                    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                    System.err.println("----- " + nodeId + " -----");
                    for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(true, true))
                    {
                        System.err.println(threadInfo);
                    }
                    System.err.println("----- " + nodeId + " -----");
                };
                serverArray.executeOn(serverFuture.getNotDoneNodeIds(), dump).get();
                for (String id : loadersFuture.getNotDoneNodeIds())
                {
                    loadersArray.executeOn(id, dump).get();
                }
                probeArray.executeOn(probeFuture.getNotDoneNodeIds(), dump).get();

                msg.append(String.format("; nodes that failed to stop: server=%s loaders=%s probe=%s",
                    serverFuture.getNotDoneNodeIds(), loadersFuture.getNotDoneNodeIds(), probeFuture.getNotDoneNodeIds()));
                LOG.error(msg.toString(), e);
            }
            catch (Exception subEx)
            {
                e.addSuppressed(subEx);
            }
            throw new Exception(msg.toString(), e);
        }
    }

    private static void waitForFutures(long time, TimeUnit unit, NodeArrayFuture... futures) throws Exception
    {
        LOG.info("  Waiting for all report files to be written...");
        Exception ex = null;
        for (NodeArrayFuture future : futures)
        {
            try
            {
                future.get(time, unit);
            }
            catch (Exception e)
            {
                if (ex == null)
                    ex = e;
                else
                    ex.addSuppressed(e);
            }
        }
        LOG.info("  All report files were written");
        if (ex != null)
            throw ex;
    }

    protected abstract void startServer(PerfTestParams perfTestParams, ClusterTools clusterTools) throws Exception;

    protected abstract void stopServer(PerfTestParams perfTestParams, ClusterTools clusterTools) throws Exception;

    protected abstract void runLoadGenerator(PerfTestParams perfTestParams, ClusterTools clusterTools) throws Exception;

    protected abstract void runProbeGenerator(PerfTestParams perfTestParams, ClusterTools clusterTools) throws Exception;
}
