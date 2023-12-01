package org.eclipse.jetty.perf.test;

import java.io.Serializable;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.perf.jdk.LocalJdk;
import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;
import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.Jvm;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleNodeArrayConfiguration;

public class PerfTestParams implements Serializable
{
    private static final String JDK_TO_USE = System.getProperty("test.jdk.name", "load-jdk17");
    private static final String OPTIONAL_MONITORED_ITEMS = System.getProperty("test.optional.monitored.items", "");

    private static final EnumSet<ConfigurableMonitor.Item> DEFAULT_MONITORED_ITEMS = EnumSet.of(
        ConfigurableMonitor.Item.CMDLINE_CPU,
        ConfigurableMonitor.Item.CMDLINE_MEMORY,
        ConfigurableMonitor.Item.CMDLINE_NETWORK,
        ConfigurableMonitor.Item.CMDLINE_DISK,
        ConfigurableMonitor.Item.PERF_STAT,
        ConfigurableMonitor.Item.JHICCUP
    );

    // MONITORED_ITEMS is static because it is needed to build CLUSTER_CONFIGURATION.
    private static final EnumSet<ConfigurableMonitor.Item> MONITORED_ITEMS = EnumSet.copyOf(new HashSet<ConfigurableMonitor.Item>() // javac 11 needs HashSet to be typed
    {{
        addAll(DEFAULT_MONITORED_ITEMS);
        addAll(ConfigurableMonitor.parseConfigurableMonitorItems(OPTIONAL_MONITORED_ITEMS));
    }});

    // CLUSTER_CONFIGURATION is static because PerfTestParams needs to be serializable and ClusterConfiguration is not.
    private static final ClusterConfiguration CLUSTER_CONFIGURATION = new SimpleClusterConfiguration()
        .jvm(new Jvm(new LocalJdk(JDK_TO_USE)))
        .nodeArray(new SimpleNodeArrayConfiguration("server")
            .node(new Node("load-master"))
            .jvm(new Jvm(new LocalJdk(JDK_TO_USE), defaultJvmOpts("-Xms32g", "-Xmx32g")))
        )
        .nodeArray(new SimpleNodeArrayConfiguration("loaders")
            .node(new Node("load-2"))
            .node(new Node("load-3"))
            .node(new Node("load-4"))
            .node(new Node("load-5"))
            .jvm(new Jvm(new LocalJdk(JDK_TO_USE), defaultJvmOpts("-Xms8g", "-Xmx8g")))
        )
        .nodeArray(new SimpleNodeArrayConfiguration("probe")
            .node(new Node("load-sample"))
            .jvm(new Jvm(new LocalJdk(JDK_TO_USE), defaultJvmOpts("-Xms8g", "-Xmx8g")))
        );

    public PerfTestParams()
    {
    }

    public Cluster buildCluster(String testName) throws Exception
    {
        return new Cluster(testName, CLUSTER_CONFIGURATION);
    }

    public HttpVersion getHttpVersion()
    {
        return HttpVersion.HTTP_1_1;
    }

    public int getParticipantCount()
    {
        return CLUSTER_CONFIGURATION.nodeArrays().stream().mapToInt(na -> na.nodes().size()).sum() + 1; // + 1 b/c of the test itself
    }

    public List<String> getNodeArrayIds()
    {
        return CLUSTER_CONFIGURATION.nodeArrays().stream().map(NodeArrayConfiguration::id).toList();
    }

    public URI getServerUri()
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

        return URI.create("http" + (isTlsEnabled() ? "s" : "") + "://" + serverHostname + ":" + getServerPort());
    }

    public int getServerPort()
    {
        return isTlsEnabled() ? 9443 : 9080;
    }

    public EnumSet<ConfigurableMonitor.Item> getMonitoredItems()
    {
        return MONITORED_ITEMS;
    }

    public int getLoaderRate()
    {
        return 60_000;
    }

    public int getProbeRate()
    {
        return 1000;
    }

    public Duration getWarmupDuration()
    {
        return Duration.ofSeconds(60);
    }

    public Duration getRunDuration()
    {
        return Duration.ofSeconds(180);
    }

    public boolean isTlsEnabled()
    {
        return false;
    }

    private static String[] defaultJvmOpts(String... extra)
    {
        List<String> result = new ArrayList<>();
        if (MONITORED_ITEMS.contains(ConfigurableMonitor.Item.GC_LOGS))
            result.addAll(List.of("-Xlog:async", "-Xlog:gc*:file=gc.log:time,level,tags")); // -Xlog:async requires jdk 17, see https://aws.amazon.com/blogs/developer/asynchronous-logging-corretto-17/
        result.add("-XX:+UseZGC");
        if (JDK_TO_USE.contains("21"))
            result.add("-XX:+ZGenerational"); // use generational ZGC on JDK 21
        result.add("-XX:+AlwaysPreTouch");
        if (MONITORED_ITEMS.contains(ConfigurableMonitor.Item.ASYNC_PROF_CPU) ||
            MONITORED_ITEMS.contains(ConfigurableMonitor.Item.ASYNC_PROF_ALLOC) ||
            MONITORED_ITEMS.contains(ConfigurableMonitor.Item.ASYNC_PROF_LOCK) ||
            MONITORED_ITEMS.contains(ConfigurableMonitor.Item.ASYNC_PROF_CACHE_MISSES))
        {
            result.addAll(List.of("-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"));
            if (JDK_TO_USE.contains("21"))
                result.add("-XX:+EnableDynamicAgentLoading"); // JDK 21 needs this flag to disable a warning when async prof is used
        }
        result.addAll(List.of(extra));
        return result.toArray(new String[0]);
    }
}
