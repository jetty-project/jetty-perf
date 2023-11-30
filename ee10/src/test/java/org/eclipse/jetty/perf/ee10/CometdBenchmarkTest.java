package org.eclipse.jetty.perf.ee10;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.perf.jdk.LocalJdk;
import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;
import org.eclipse.jetty.perf.util.OutputCapturer;
import org.eclipse.jetty.perf.util.ReportUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.ClusterTools;
import org.mortbay.jetty.orchestrator.NodeArray;
import org.mortbay.jetty.orchestrator.NodeArrayFuture;
import org.mortbay.jetty.orchestrator.NodeJob;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.Jvm;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleNodeArrayConfiguration;
import org.mortbay.jetty.orchestrator.tools.Barrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled("Need a new CometD release")
public class CometdBenchmarkTest implements Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(CometdBenchmarkTest.class);

    private static final String JDK_TO_USE = System.getProperty("test.jdk.name", "load-jdk17");

    private static final EnumSet<ConfigurableMonitor.Item> MONITORED_ITEMS = EnumSet.of(
        ConfigurableMonitor.Item.CMDLINE_CPU,
        ConfigurableMonitor.Item.CMDLINE_MEMORY,
        ConfigurableMonitor.Item.CMDLINE_NETWORK,
        ConfigurableMonitor.Item.CMDLINE_DISK,
        ConfigurableMonitor.Item.ASYNC_PROF_CPU, // Async Profiler seems to be the cause of the 59th second latency spike.
        ConfigurableMonitor.Item.JHICCUP
    );

    private static String[] defaultJvmOpts(String... extra)
    {
        List<String> result = new ArrayList<>();
        result.add("-XX:+UnlockExperimentalVMOptions");
        result.add("-XX:+UseZGC");
        result.add("-XX:+AlwaysPreTouch");
        if (MONITORED_ITEMS.contains(ConfigurableMonitor.Item.ASYNC_PROF_CPU) ||
            MONITORED_ITEMS.contains(ConfigurableMonitor.Item.ASYNC_PROF_ALLOC) ||
            MONITORED_ITEMS.contains(ConfigurableMonitor.Item.ASYNC_PROF_LOCK) ||
            MONITORED_ITEMS.contains(ConfigurableMonitor.Item.ASYNC_PROF_CACHE_MISSES))
            result.addAll(List.of("-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"));
        result.addAll(Arrays.asList(extra));
        return result.toArray(new String[0]);
    }

    private static final ClusterConfiguration CLUSTER_CONFIGURATION = new SimpleClusterConfiguration()
        .jvm(new Jvm(new LocalJdk(JDK_TO_USE)))
        .nodeArray(new SimpleNodeArrayConfiguration("server")
            .node(new Node("load-master"))
            .jvm(new Jvm(new LocalJdk(JDK_TO_USE), defaultJvmOpts("-Xms32g", "-Xmx32g")))
        )
        .nodeArray(new SimpleNodeArrayConfiguration("clients")
            .node(new Node("load-2"))
            .node(new Node("load-3"))
            .node(new Node("load-4"))
            .node(new Node("load-5"))
            .jvm(new Jvm(new LocalJdk(JDK_TO_USE), defaultJvmOpts("-Xms8g", "-Xmx8g")))
        );

    private String testName;

    @BeforeEach
    protected void beforeEach(TestInfo testInfo)
    {
        // Generate test name
        String className = testInfo.getTestClass().orElseThrow().getName();
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        String methodName = testInfo.getTestMethod().orElseThrow().getName();
        testName = simpleClassName + "_" + methodName;
    }

    @Test
    public void testStandard() throws Exception
    {
        Path reportRootPath = ReportUtil.createReportRootPath(testName);
        try (OutputCapturer ignore = new OutputCapturer(reportRootPath);
            Cluster cluster = new Cluster(testName, CLUSTER_CONFIGURATION))
        {
            NodeArray serverArray = cluster.nodeArray("server");
            NodeArray clientsArray = cluster.nodeArray("clients");

            NodeJob logSystemProps = tools -> LOG.info("JVM version '{}' running on '{}/{}'", System.getProperty("java.vm.version"), System.getProperty("os.name"), System.getProperty("os.arch"));
            serverArray.executeOnAll(logSystemProps).get();
            clientsArray.executeOnAll(logSystemProps).get();

            serverArray.executeOnAll(this::startMonitoring).get();
            clientsArray.executeOnAll(this::startMonitoring).get();

            NodeArrayFuture serverArrayFuture = serverArray.executeOnAll(tools ->
            {
                List<String> args = Arrays.asList("--auto", "--transports=jetty");
                // TODO
//                CometDLoadServer.main(args.toArray(new String[0]));
            });

            NodeArrayFuture clientArrayFuture = clientsArray.executeOnAll(tools ->
            {
                Barrier barrier = tools.barrier("clientId", getClientsCount());
                int clientId = barrier.await();
                List<String> args = Arrays.asList("--auto", "--host=" + getServerHostname(), "--transport=LONG_POLLING", "--channel=/a/" + clientId);
                // TODO
//                CometDLoadClient.main(args.toArray(new String[0]));
            });

            serverArrayFuture.get(120, TimeUnit.SECONDS);
            clientArrayFuture.get(120, TimeUnit.SECONDS);

            serverArray.executeOnAll(this::stopMonitoring).get();
            clientsArray.executeOnAll(this::stopMonitoring).get();

            ReportUtil.download(serverArray, reportRootPath.resolve("server"));
            ReportUtil.transformJHiccupHisto(serverArray, reportRootPath.resolve("server"));
            ReportUtil.download(clientsArray, reportRootPath.resolve("clients"));
            ReportUtil.transformJHiccupHisto(clientsArray, reportRootPath.resolve("clients"));
        }
    }

    private void startMonitoring(ClusterTools tools) throws Exception
    {
        ConfigurableMonitor monitor = new ConfigurableMonitor(MONITORED_ITEMS);
        tools.nodeEnvironment().put(ConfigurableMonitor.class.getName(), monitor);
    }

    private void stopMonitoring(ClusterTools tools)
    {
        ConfigurableMonitor monitor = (ConfigurableMonitor)tools.nodeEnvironment().get(ConfigurableMonitor.class.getName());
        if (monitor != null)
            monitor.close();
    }

    private static int getClientsCount()
    {
        return CLUSTER_CONFIGURATION.nodeArrays().stream().filter(nac -> nac.id().equals("clients")).mapToInt(nac -> nac.nodes().size()).sum();
    }

    public String getServerHostname()
    {
        String serverHostname = null;
        for (NodeArrayConfiguration nodeArrayConfiguration : CLUSTER_CONFIGURATION.nodeArrays())
        {
            if (!nodeArrayConfiguration.id().equals("server"))
                continue;

            Collection<Node> nodes = nodeArrayConfiguration.nodes();
            if (nodes.size() != 1)
                throw new IllegalStateException("server node array must contain only one node");
            serverHostname = nodes.iterator().next().getHostname();
        }
        if (serverHostname == null)
            throw new IllegalStateException("cluster configuration must have a node array named 'server'");

        return serverHostname;
    }
}
