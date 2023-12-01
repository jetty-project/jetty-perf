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

import org.eclipse.jetty.client.ConnectionPool;
import org.eclipse.jetty.client.DuplexConnectionPool;
import org.eclipse.jetty.client.RandomConnectionPool;
import org.eclipse.jetty.client.RoundRobinConnectionPool;
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
    // Must not be final as we want the test instance's read values.
    private final String JDK_TO_USE = readConfigSetting("JDK_TO_USE");
    private final String OPTIONAL_MONITORED_ITEMS = readConfigSetting("OPTIONAL_MONITORED_ITEMS");
    private final String SERVER_NAME = readConfigSetting("SERVER_NAME");
    private final String SERVER_JVM_OPTS = readConfigSetting("SERVER_JVM_OPTS");
    private final String LOADER_NAMES = readConfigSetting("LOADER_NAMES");
    private final String LOADER_JVM_OPTS = readConfigSetting("LOADER_JVM_OPTS");
    private final String LOADER_CONNECTION_POOL_FACTORY_TYPE = readConfigSetting("LOADER_CONNECTION_POOL_FACTORY_TYPE");
    private final String LOADER_CONNECTION_POOL_MAX_CONNECTION_PER_DESTINATION = readConfigSetting("LOADER_CONNECTION_POOL_MAX_CONNECTION_PER_DESTINATION");
    private final String PROBE_NAME = readConfigSetting("PROBE_NAME");
    private final String PROBE_JVM_OPTS = readConfigSetting("PROBE_JVM_OPTS");
    private final String WARMUP_DURATION = readConfigSetting("WARMUP_DURATION");
    private final String RUN_DURATION = readConfigSetting("RUN_DURATION");
    private final String LOADER_RATE = readConfigSetting("LOADER_RATE");
    private final String PROBE_RATE = readConfigSetting("PROBE_RATE");

    private static String readConfigSetting(String name)
    {
        String env = System.getenv(name);
        if (env == null)
            return "";
        return env.trim();
    }

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

    // Do not serialize this field, let it be reconstructed on each node.
    private transient ClusterConfiguration clusterConfiguration;

    public PerfTestParams()
    {
    }

    public ConnectionPool.Factory buildLoaderConnectionPoolFactory()
    {
        int tmp;
        try
        {
            tmp = Integer.parseInt(LOADER_CONNECTION_POOL_MAX_CONNECTION_PER_DESTINATION);
        }
        catch (NumberFormatException e)
        {
            tmp = -1;
        }
        int maxConnectionPerDestination = tmp;

        return switch (LOADER_CONNECTION_POOL_FACTORY_TYPE)
        {
            case "random" ->
                destination -> new RandomConnectionPool(destination, maxConnectionPerDestination > 0 ? maxConnectionPerDestination : destination.getHttpClient().getMaxConnectionsPerDestination(), 1);
            case "round-robin" ->
                destination -> new RoundRobinConnectionPool(destination, maxConnectionPerDestination > 0 ? maxConnectionPerDestination : destination.getHttpClient().getMaxConnectionsPerDestination());
            default ->
                destination -> new DuplexConnectionPool(destination, maxConnectionPerDestination > 0 ? maxConnectionPerDestination : destination.getHttpClient().getMaxConnectionsPerDestination());
        };
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

    private String[] defaultJvmOpts(String extraArgLine)
    {
        List<String> extra = Arrays.stream(extraArgLine.split(" ")).map(String::trim).toList();

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
