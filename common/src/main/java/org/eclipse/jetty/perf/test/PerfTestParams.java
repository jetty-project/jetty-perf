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
import org.eclipse.jetty.client.MultiplexConnectionPool;
import org.eclipse.jetty.client.RandomConnectionPool;
import org.eclipse.jetty.client.RoundRobinConnectionPool;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.perf.jdk.LocalJdk;
import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;
import org.eclipse.jetty.perf.util.JenkinsParameters;
import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.Jvm;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleNodeArrayConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerfTestParams implements Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(PerfTestParams.class);

    // Must not be static as we want the test instance's values.
    private final JenkinsParameters parameters = new JenkinsParameters();

    public String JDK_TO_USE = parameters.read("JDK_TO_USE", "load-jdk17");
    public String OPTIONAL_MONITORED_ITEMS = parameters.read("OPTIONAL_MONITORED_ITEMS", "");
    public String SERVER_NAME = parameters.read("SERVER_NAME", "localhost");
    public String SERVER_JVM_OPTS = parameters.read("SERVER_JVM_OPTS", "");
    public String LOADER_NAMES = parameters.read("LOADER_NAMES", "localhost");
    public String LOADER_JVM_OPTS = parameters.read("LOADER_JVM_OPTS", "");
    public String PROBE_NAME = parameters.read("PROBE_NAME", "localhost");
    public String PROBE_JVM_OPTS = parameters.read("PROBE_JVM_OPTS", "");
    public String LOADER_CONNECTION_POOL_FACTORY_TYPE = parameters.read("LOADER_CONNECTION_POOL_FACTORY_TYPE", "first");
    public int LOADER_CONNECTION_POOL_MAX_CONNECTIONS_PER_DESTINATION = parameters.readAsInt("LOADER_CONNECTION_POOL_MAX_CONNECTIONS_PER_DESTINATION", -1);
    public boolean LOADER_PRECREATE_CONNECTIONS = parameters.readAsBoolean("LOADER_PRECREATE_CONNECTIONS", false);
    public int WARMUP_DURATION = parameters.readAsInt("WARMUP_DURATION", 10);
    public int RUN_DURATION = parameters.readAsInt("RUN_DURATION", 20);
    public int LOADER_RATE = parameters.readAsInt("LOADER_RATE", 60000);
    public int PROBE_RATE = parameters.readAsInt("PROBE_RATE", 6000);
    public int SERVER_ACCEPTOR_COUNT = parameters.readAsInt("SERVER_ACCEPTOR_COUNT", -1);
    public int SERVER_SELECTOR_COUNT = parameters.readAsInt("SERVER_SELECTOR_COUNT", -1);
    public boolean SERVER_USE_VIRTUAL_THREADS = parameters.readAsBoolean("SERVER_USE_VIRTUAL_THREADS", false);
    public String HTTP_PROTOCOL = parameters.read("HTTP_PROTOCOL", "http");

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
        boolean preCreate = LOADER_PRECREATE_CONNECTIONS;
        int connections = LOADER_CONNECTION_POOL_MAX_CONNECTIONS_PER_DESTINATION;
        switch (LOADER_CONNECTION_POOL_FACTORY_TYPE)
        {
            case "random":
                return destination ->
                {
                    RandomConnectionPool pool = new RandomConnectionPool(destination, connections > 0 ? connections : destination.getHttpClient().getMaxConnectionsPerDestination(), 1);
                    if (preCreate)
                        pool.preCreateConnections(connections);
                    return pool;
                };
            case "round-robin":
                return destination ->
                {
                    RoundRobinConnectionPool pool = new RoundRobinConnectionPool(destination, connections > 0 ? connections : destination.getHttpClient().getMaxConnectionsPerDestination(), 1);
                    if (preCreate)
                        pool.preCreateConnections(connections);
                    return pool;
                };
            default:
                LOG.warn("Unsupported LOADER_CONNECTION_POOL_FACTORY_TYPE '{}', defaulting to 'first'", LOADER_CONNECTION_POOL_FACTORY_TYPE);
            case "first":
                if (getHttpVersion().getVersion() <= 11)
                    return destination ->
                    {
                        DuplexConnectionPool pool = new DuplexConnectionPool(destination, connections > 0 ? connections : destination.getHttpClient().getMaxConnectionsPerDestination());
                        if (preCreate)
                            pool.preCreateConnections(connections);
                        return pool;
                    };
                else
                    return destination ->
                    {
                        MultiplexConnectionPool pool = new MultiplexConnectionPool(destination, connections > 0 ? connections : destination.getHttpClient().getMaxConnectionsPerDestination(), 1);
                        if (preCreate)
                            pool.preCreateConnections(connections);
                        return pool;
                    };
        }
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
        return SERVER_ACCEPTOR_COUNT;
    }

    public int getServerSelectorCount()
    {
        return SERVER_SELECTOR_COUNT;
    }

    public int getServerPort()
    {
        return isTlsEnabled() ? 9443 : 9080;
    }

    public int getLoaderRate()
    {
        return LOADER_RATE;
    }

    public int getProbeRate()
    {
        return PROBE_RATE;
    }

    public Duration getWarmupDuration()
    {
        return Duration.ofSeconds(WARMUP_DURATION);
    }

    public Duration getRunDuration()
    {
        return Duration.ofSeconds(RUN_DURATION);
    }

    public HttpVersion getHttpVersion()
    {
        switch (HTTP_PROTOCOL)
        {
            case "h2c":
                return HttpVersion.HTTP_2;
            default:
                LOG.warn("Unsupported HTTP_PROTOCOL '{}', defaulting to 'http'", HTTP_PROTOCOL);
            case "http":
                return HttpVersion.HTTP_1_1;
        }
    }

    public boolean isTlsEnabled()
    {
        return switch (HTTP_PROTOCOL)
        {
            case "https", "h2" -> true;
            default -> false;
        };
    }
}
