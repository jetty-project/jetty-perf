package org.eclipse.jetty.perf.test;

import java.io.Serializable;
import java.net.URI;
import java.security.Security;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.conscrypt.OpenSSLProvider;
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
    public int WARMUP_DURATION = parameters.readAsInt("WARMUP_DURATION", 10);
    public int RUN_DURATION = parameters.readAsInt("RUN_DURATION", 20);
    public int LOADER_RATE = parameters.readAsInt("LOADER_RATE", 30000);
    public int LOADER_THREADS = parameters.readAsInt("LOADER_THREADS", -1);
    public int PROBE_RATE = parameters.readAsInt("PROBE_RATE", 6000);
    public int SERVER_ACCEPTOR_COUNT = parameters.readAsInt("SERVER_ACCEPTOR_COUNT", -1);
    public int SERVER_SELECTOR_COUNT = parameters.readAsInt("SERVER_SELECTOR_COUNT", -1);
    public boolean SERVER_USE_VIRTUAL_THREADS = parameters.readAsBoolean("SERVER_USE_VIRTUAL_THREADS", false);
    public boolean SERVER_USE_BYTE_BUFFER_POOLING = parameters.readAsBoolean("SERVER_USE_BYTE_BUFFER_POOLING", true);
    public int SERVER_THREAD_POOL_SIZE = parameters.readAsInt("SERVER_THREAD_POOL_SIZE", 200);
    public int SERVER_RESERVED_THREADS = parameters.readAsInt("SERVER_RESERVED_THREADS", -1);
    public String HTTP_PROTOCOL = parameters.read("HTTP_PROTOCOL", "http");
    public String JSSE_PROVIDER = parameters.read("JSSE_PROVIDER", "");

    private static final EnumSet<ConfigurableMonitor.Item> DEFAULT_MONITORED_ITEMS = EnumSet.of(
        ConfigurableMonitor.Item.OS_CPU,
        ConfigurableMonitor.Item.OS_MEMORY,
        ConfigurableMonitor.Item.OS_NETWORK,
        ConfigurableMonitor.Item.OS_DISK,
        ConfigurableMonitor.Item.OS_PERF_STAT,
        ConfigurableMonitor.Item.JHICCUP,
        ConfigurableMonitor.Item.JIT_COMPILATION_TIME
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

    public Map<String, Object> asMap()
    {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("JDK_TO_USE", JDK_TO_USE);
        result.put("OPTIONAL_MONITORED_ITEMS", OPTIONAL_MONITORED_ITEMS);
        result.put("SERVER_NAME", SERVER_NAME);
        result.put("SERVER_JVM_OPTS", SERVER_JVM_OPTS);
        result.put("LOADER_NAMES", LOADER_NAMES);
        result.put("LOADER_JVM_OPTS", LOADER_JVM_OPTS);
        result.put("PROBE_NAME", PROBE_NAME);
        result.put("PROBE_JVM_OPTS", PROBE_JVM_OPTS);
        result.put("LOADER_CONNECTION_POOL_FACTORY_TYPE", LOADER_CONNECTION_POOL_FACTORY_TYPE);
        result.put("LOADER_CONNECTION_POOL_MAX_CONNECTIONS_PER_DESTINATION", LOADER_CONNECTION_POOL_MAX_CONNECTIONS_PER_DESTINATION);
        result.put("WARMUP_DURATION", WARMUP_DURATION);
        result.put("RUN_DURATION", RUN_DURATION);
        result.put("LOADER_RATE", LOADER_RATE);
        result.put("LOADER_THREADS", LOADER_THREADS);
        result.put("PROBE_RATE", PROBE_RATE);
        result.put("SERVER_ACCEPTOR_COUNT", SERVER_ACCEPTOR_COUNT);
        result.put("SERVER_SELECTOR_COUNT", SERVER_SELECTOR_COUNT);
        result.put("SERVER_USE_VIRTUAL_THREADS", SERVER_USE_VIRTUAL_THREADS);
        result.put("HTTP_PROTOCOL", HTTP_PROTOCOL);
        result.put("JSSE_PROVIDER", JSSE_PROVIDER);

        return result;
    }

    public ConnectionPool.Factory buildLoaderConnectionPoolFactory()
    {
        int connections = LOADER_CONNECTION_POOL_MAX_CONNECTIONS_PER_DESTINATION;
        switch (LOADER_CONNECTION_POOL_FACTORY_TYPE)
        {
            case "random":
                return destination -> new RandomConnectionPool(destination, connections > 0 ? connections : destination.getHttpClient().getMaxConnectionsPerDestination(), 1);
            case "round-robin":
                return destination -> new RoundRobinConnectionPool(destination, connections > 0 ? connections : destination.getHttpClient().getMaxConnectionsPerDestination(), 1);
            default:
                LOG.warn("Unsupported LOADER_CONNECTION_POOL_FACTORY_TYPE '{}', defaulting to 'first'", LOADER_CONNECTION_POOL_FACTORY_TYPE);
            case "first":
                if (getHttpVersion().getVersion() <= 11)
                    return destination -> new DuplexConnectionPool(destination, connections > 0 ? connections : destination.getHttpClient().getMaxConnectionsPerDestination());
                else
                    return destination -> new MultiplexConnectionPool(destination, connections > 0 ? connections : destination.getHttpClient().getMaxConnectionsPerDestination(), 1);
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

    public int getLoaderThreads()
    {
        if (LOADER_THREADS < 1)
            return Runtime.getRuntime().availableProcessors();
        return LOADER_THREADS;
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
        return switch (HTTP_PROTOCOL)
        {
            case "http", "https" -> HttpVersion.HTTP_1_1;
            case "h2c", "h2" -> HttpVersion.HTTP_2;
            default ->
            {
                LOG.warn("Unsupported HTTP_PROTOCOL '{}', defaulting to 'http'", HTTP_PROTOCOL);
                yield HttpVersion.HTTP_1_1;
            }
        };
    }

    public boolean isTlsEnabled()
    {
        return switch (HTTP_PROTOCOL)
        {
            case "https", "h2" -> true;
            default -> false;
        };
    }

    public String getJsseProvider()
    {
        switch (JSSE_PROVIDER)
        {
            case "Conscrypt" ->
                Security.addProvider(new OpenSSLProvider());
            case "BCJSSE" ->
            {
                Security.addProvider(new BouncyCastleProvider());
                Security.addProvider(new BouncyCastleJsseProvider());
            }
            default -> {}
        }
        return JSSE_PROVIDER;
    }
}
