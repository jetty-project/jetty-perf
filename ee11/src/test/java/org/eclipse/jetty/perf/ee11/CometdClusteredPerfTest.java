package org.eclipse.jetty.perf.ee11;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.cometd.benchmark.client.CometDLoadClient;
import org.cometd.benchmark.server.CometDLoadServer;
import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;
import org.eclipse.jetty.perf.test.AbstractClusteredPerfTest;
import org.eclipse.jetty.perf.test.ClusteredTestContext;
import org.eclipse.jetty.perf.test.PerfTestParams;
import org.eclipse.jetty.perf.util.SerializableConsumer;
import org.mortbay.jetty.orchestrator.ClusterTools;
import org.mortbay.jetty.orchestrator.NodeArray;
import org.mortbay.jetty.orchestrator.NodeArrayFuture;
import org.mortbay.jetty.orchestrator.NodeJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.perf.util.ReportUtil.generateReport;

public class CometdClusteredPerfTest extends AbstractClusteredPerfTest
{
    private static final Logger LOG = LoggerFactory.getLogger(CometdClusteredPerfTest.class);

    private CometdClusteredPerfTest(String testName, Path reportRootPath, PerfTestParams perfTestParams, SerializableConsumer<PerfTestParams> perfTestParamsCustomizer) throws Exception
    {
        super(testName, reportRootPath, perfTestParams, perfTestParamsCustomizer);
    }

    public static void runTest(ClusteredTestContext clusteredTestContext) throws Exception
    {
        runTest(clusteredTestContext, new PerfTestParams(), p -> {});
    }

    public static void runTest(ClusteredTestContext clusteredTestContext, PerfTestParams perfTestParams, SerializableConsumer<PerfTestParams> perfTestParamsCustomizer) throws Exception
    {
        try (CometdClusteredPerfTest clusteredPerfTest = new CometdClusteredPerfTest(clusteredTestContext.getTestName(), clusteredTestContext.getReportRootPath(), perfTestParams, perfTestParamsCustomizer))
        {
            clusteredPerfTest.execute();
        }
    }

    @Override
    protected void execute() throws Exception
    {
        LOG.info("Parameters:");
        perfTestParams.asMap().forEach((k, v) -> System.out.println("  " + k + " = '" + v + "'"));

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

        LOG.info("Warming up the server...");
        serverArray.executeOnAll(tools ->
        {
            perfTestParamsCustomizer.accept(perfTestParams);
            runServer(perfTestParams, tools);
        }).get(120, TimeUnit.SECONDS);
        LOG.info("Warming up the clients...");
        loadersArray.executeOnAll(tools ->
        {
            perfTestParamsCustomizer.accept(perfTestParams);
            runClient(perfTestParams, tools);
        }).get(120, TimeUnit.SECONDS);

        LOG.info("Running...");
        long before = System.nanoTime();

        serverArray.executeOnAll(tools ->
        {
            ConfigurableMonitor configurableMonitor = new ConfigurableMonitor(perfTestParams.getMonitoredItems());
            tools.nodeEnvironment().put(ConfigurableMonitor.class.getName(), configurableMonitor);
            perfTestParamsCustomizer.accept(perfTestParams);
            runServer(perfTestParams, tools);
        }).get(120, TimeUnit.SECONDS);
        loadersArray.executeOnAll(tools ->
        {
            try (ConfigurableMonitor ignore = new ConfigurableMonitor(perfTestParams.getMonitoredItems()))
            {
                perfTestParamsCustomizer.accept(perfTestParams);
                runClient(perfTestParams, tools);
            }
        }).get(120, TimeUnit.SECONDS);
        serverArray.executeOnAll(tools ->
        {
            ConfigurableMonitor configurableMonitor = (ConfigurableMonitor)tools.nodeEnvironment().get(ConfigurableMonitor.class.getName());
            configurableMonitor.close();
        }).get(120, TimeUnit.SECONDS);

        LOG.info("Generating report...");
        generateReport(Path.of(reportRootPath), perfTestParams.getNodeArrayIds(), cluster);

        long after = System.nanoTime();
        LOG.info("Done; elapsed={} ms", TimeUnit.NANOSECONDS.toMillis(after - before));
    }

    protected void runServer(PerfTestParams perfTestParams, ClusterTools clusterTools) throws Exception
    {
        CometDLoadServer.main(merge(perfTestParams.COMETD_CLIENTS_CMDLINE,"--auto", "--transports=jetty"));
    }

    protected void runClient(PerfTestParams perfTestParams, ClusterTools clusterTools) throws Exception
    {
        int clientId = clusterTools.barrier("cometd-client-id-barrier", perfTestParams.getLoadersCount()).await();
        CometDLoadClient.main(merge(perfTestParams.COMETD_SERVER_CMDLINE,"--auto", "--host=" + perfTestParams.getServerUri().getHost(), "--channel=/a/" + clientId));
    }

    private static String[] merge(String argsLine, String... extraArgs)
    {
        List<String> result = new ArrayList<>();
        Stream.of(argsLine.split(" ")).map(String::trim).filter(s -> !s.isEmpty()).forEach(result::add);
        result.addAll(List.of(extraArgs));
        return result.toArray(new String[0]);
    }
}
