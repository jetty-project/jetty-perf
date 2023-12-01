package org.eclipse.jetty.perf.test;

import java.io.Serializable;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final String JDK_TO_USE = System.getenv("_JDK_TO_USE").trim();
    private static final String OPTIONAL_MONITORED_ITEMS = System.getenv("_OPTIONAL_MONITORED_ITEMS").trim();
    private static final String SERVER_NAME = System.getenv("_SERVER_NAME").trim();
    private static final String SERVER_JVM_OPTS = System.getenv("_SERVER_JVM_OPTS").trim();
    private static final String LOADER_NAMES = System.getenv("_LOADER_NAMES").trim();
    private static final String LOADER_JVM_OPTS = System.getenv("_LOADER_JVM_OPTS").trim();
    private static final String PROBE_NAME = System.getenv("_PROBE_NAME").trim();
    private static final String PROBE_JVM_OPTS = System.getenv("_PROBE_JVM_OPTS").trim();
    private static final String WARMUP_DURATION = System.getenv("_WARMUP_DURATION").trim();
    private static final String RUN_DURATION = System.getenv("_RUN_DURATION").trim();
    private static final String LOADER_RATE = System.getenv("_LOADER_RATE").trim();
    private static final String PROBE_RATE = System.getenv("_PROBE_RATE").trim();

    private static final EnumSet<ConfigurableMonitor.Item> DEFAULT_MONITORED_ITEMS = EnumSet.of(
        ConfigurableMonitor.Item.CMDLINE_CPU,
        ConfigurableMonitor.Item.CMDLINE_MEMORY,
        ConfigurableMonitor.Item.CMDLINE_NETWORK,
        ConfigurableMonitor.Item.CMDLINE_DISK,
        ConfigurableMonitor.Item.PERF_STAT,
        ConfigurableMonitor.Item.JHICCUP
    );

    private final EnumSet<ConfigurableMonitor.Item> monitoredItems = EnumSet.copyOf(new HashSet<ConfigurableMonitor.Item>() // javac 11 needs HashSet to be typed
    {{
        addAll(DEFAULT_MONITORED_ITEMS);
        addAll(ConfigurableMonitor.parseConfigurableMonitorItems(OPTIONAL_MONITORED_ITEMS));
    }});

    private ClusterConfiguration clusterConfiguration;

    public PerfTestParams()
    {
    }

    private ClusterConfiguration getClusterConfiguration()
    {
        if (clusterConfiguration == null)
        {
            if (SERVER_NAME.isEmpty())
                throw new IllegalArgumentException("Server name cannot be empty");
            SimpleNodeArrayConfiguration serverNodeArrayConfig = new SimpleNodeArrayConfiguration("server")
                .jvm(new Jvm(new LocalJdk(JDK_TO_USE), defaultJvmOpts(SERVER_JVM_OPTS)))
                .node(new Node(SERVER_NAME));

            if (LOADER_NAMES.isEmpty())
                throw new IllegalArgumentException("Loader names cannot be empty");
            SimpleNodeArrayConfiguration loadersNodeArrayConfig = new SimpleNodeArrayConfiguration("loaders")
                .jvm(new Jvm(new LocalJdk(JDK_TO_USE), defaultJvmOpts(LOADER_JVM_OPTS)));
            List<String> loaderNames = Arrays.stream(LOADER_NAMES.split(",")).map(String::trim).toList();
            for (String loaderName : loaderNames)
            {
                if (loaderName.isEmpty())
                    throw new IllegalArgumentException("Loader names CSV list must not contain empty entries: " + LOADER_NAMES);
                loadersNodeArrayConfig.node(new Node(loaderName));
            }

            if (PROBE_NAME.isEmpty())
                throw new IllegalArgumentException("Probe name cannot be empty");
            SimpleNodeArrayConfiguration probeNodeArrayConfig = new SimpleNodeArrayConfiguration("probe")
                .jvm(new Jvm(new LocalJdk(JDK_TO_USE), defaultJvmOpts(PROBE_JVM_OPTS)))
                .node(new Node(PROBE_NAME));

            clusterConfiguration = new SimpleClusterConfiguration()
                .jvm(new Jvm(new LocalJdk(JDK_TO_USE)))
                .nodeArray(serverNodeArrayConfig)
                .nodeArray(loadersNodeArrayConfig)
                .nodeArray(probeNodeArrayConfig);
        }
        return clusterConfiguration;
    }

    private String[] defaultJvmOpts(String extraCsv)
    {
        List<String> extra = Arrays.stream(extraCsv.split(",")).map(String::trim).toList();

        List<String> result = new ArrayList<>();
        if (monitoredItems.contains(ConfigurableMonitor.Item.GC_LOGS))
            result.addAll(List.of("-Xlog:async", "-Xlog:gc*:file=gc.log:time,level,tags")); // -Xlog:async requires jdk 17, see https://aws.amazon.com/blogs/developer/asynchronous-logging-corretto-17/
        result.add("-XX:+UseZGC");
        if (JDK_TO_USE.contains("21"))
            result.add("-XX:+ZGenerational"); // use generational ZGC on JDK 21
        result.add("-XX:+AlwaysPreTouch");
        if (monitoredItems.contains(ConfigurableMonitor.Item.ASYNC_PROF_CPU) ||
            monitoredItems.contains(ConfigurableMonitor.Item.ASYNC_PROF_ALLOC) ||
            monitoredItems.contains(ConfigurableMonitor.Item.ASYNC_PROF_LOCK) ||
            monitoredItems.contains(ConfigurableMonitor.Item.ASYNC_PROF_CACHE_MISSES))
        {
            result.addAll(List.of("-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"));
            if (JDK_TO_USE.contains("21"))
                result.add("-XX:+EnableDynamicAgentLoading"); // JDK 21 needs this flag to disable a warning when async prof is used
        }
        result.addAll(extra);
        return result.toArray(new String[0]);
    }

    public EnumSet<ConfigurableMonitor.Item> getMonitoredItems()
    {
        return monitoredItems;
    }

    public Cluster buildCluster(String testName) throws Exception
    {
        return new Cluster(testName, getClusterConfiguration());
    }

    public int getParticipantCount()
    {
        return getClusterConfiguration().nodeArrays().stream().mapToInt(na -> na.nodes().size()).sum() + 1; // + 1 b/c of the test itself
    }

    public List<String> getNodeArrayIds()
    {
        return getClusterConfiguration().nodeArrays().stream().map(NodeArrayConfiguration::id).toList();
    }

    public URI getServerUri()
    {
        String serverHostname = null;
        for (NodeArrayConfiguration nodeArrayConfiguration : getClusterConfiguration().nodeArrays())
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

    public int getServerAcceptorCount()
    {
        return 4;
    }

    public int getServerSelectorCount()
    {
        return 24;
    }

    public int getServerPort()
    {
        return isTlsEnabled() ? 9443 : 9080;
    }

    public int getLoaderRate()
    {
        return Integer.parseInt(LOADER_RATE);
    }

    public int getProbeRate()
    {
        return Integer.parseInt(PROBE_RATE);
    }

    public Duration getWarmupDuration()
    {
        return Duration.ofSeconds(Integer.parseInt(WARMUP_DURATION));
    }

    public Duration getRunDuration()
    {
        return Duration.ofSeconds(Integer.parseInt(RUN_DURATION));
    }

    public HttpVersion getHttpVersion()
    {
        return HttpVersion.HTTP_1_1;
    }

    public boolean isTlsEnabled()
    {
        return false;
    }
}
