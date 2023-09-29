package org.eclipse.jetty.perf.test;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import org.eclipse.jetty.perf.jdk.LocalJdk;
import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.Jvm;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleNodeArrayConfiguration;

public class PerfTestParams implements Serializable
{
    private static final String JDK_TO_USE = System.getProperty("test.jdk.name", "load-jdk17");

    private static final EnumSet<ConfigurableMonitor.Item> MONITORED_ITEMS = EnumSet.of(
        ConfigurableMonitor.Item.CMDLINE_CPU,
        ConfigurableMonitor.Item.CMDLINE_MEMORY,
        ConfigurableMonitor.Item.CMDLINE_NETWORK,
        //ConfigurableMonitor.Item.ASYNC_PROF_CPU, // Async Profiler seems to be the cause of the 59th second latency spike.
        ConfigurableMonitor.Item.ASYNC_PROF_ALLOCATION,
        ConfigurableMonitor.Item.JHICCUP,
        ConfigurableMonitor.Item.GC_LOGS
    );

    private static final ClusterConfiguration CLUSTER_CONFIGURATION = new SimpleClusterConfiguration()
        .jvm(new Jvm(new LocalJdk(JDK_TO_USE)))
        .nodeArray(new SimpleNodeArrayConfiguration("server")
            .node(new Node("load-master"))
            .jvm(new Jvm(new LocalJdk(JDK_TO_USE), defaultJvmOpts("-Xms32g", "-Xmx32g")))
        )
        .nodeArray(new SimpleNodeArrayConfiguration("loaders")
            .node(new Node("load-1"))
            .node(new Node("load-3"))
            .node(new Node("load-4"))
            .node(new Node("load-5"))
            .jvm(new Jvm(new LocalJdk(JDK_TO_USE), defaultJvmOpts("-Xms8g", "-Xmx8g")))
        )
        .nodeArray(new SimpleNodeArrayConfiguration("probe")
            .node(new Node("load-sample"))
            .jvm(new Jvm(new LocalJdk(JDK_TO_USE), defaultJvmOpts("-Xint", "-Xms8g", "-Xmx8g")))
        );

    public enum Protocol
    {
        http(false, HttpVersion.HTTP11),
        https(true, HttpVersion.HTTP11),
        h2c(false, HttpVersion.HTTP2),
        h2(true, HttpVersion.HTTP2),
        ;

        private final boolean secure;
        private final HttpVersion version;

        Protocol(boolean secure, HttpVersion version)
        {
            this.secure = secure;
            this.version = version;
        }

        public boolean isSecure()
        {
            return secure;
        }

        public HttpVersion getVersion()
        {
            return version;
        }
    }

    public enum HttpVersion
    {
        HTTP11, HTTP2
    }

    private final Protocol protocol;
    private final int loaderRate;
    private final int probeRate;
    private final long expectedP99ServerLatency;
    private final long expectedP99ProbeLatency;
    private final double expectedP99ErrorMargin;

    public PerfTestParams(Protocol protocol, int loaderRate, int probeRate, long expectedP99ServerLatency, long expectedP99ProbeLatency, double expectedP99ErrorMargin)
    {
        this.protocol = protocol;
        this.loaderRate = loaderRate;
        this.probeRate = probeRate;
        this.expectedP99ServerLatency = expectedP99ServerLatency;
        this.expectedP99ProbeLatency = expectedP99ProbeLatency;
        this.expectedP99ErrorMargin = expectedP99ErrorMargin;
    }

    public Protocol getProtocol()
    {
        return protocol;
    }

    public ClusterConfiguration getClusterConfiguration()
    {
        return CLUSTER_CONFIGURATION;
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

        return URI.create("http" + (getProtocol().isSecure() ? "s" : "") + "://" + serverHostname + ":" + getServerPort());
    }

    public int getServerPort()
    {
        return getProtocol().isSecure() ? 9443 : 9080;
    }

    public EnumSet<ConfigurableMonitor.Item> getMonitoredItems()
    {
        return MONITORED_ITEMS;
    }

    public int getLoaderRate()
    {
        return loaderRate;
    }

    public int getProbeRate()
    {
        return probeRate;
    }

    public long getExpectedP99ServerLatency()
    {
        return expectedP99ServerLatency;
    }

    public long getExpectedP99ProbeLatency()
    {
        return expectedP99ProbeLatency;
    }

    public double getExpectedP99ErrorMargin()
    {
        return expectedP99ErrorMargin;
    }

    @Override
    public String toString()
    {
        return protocol.name();
    }

    private static String[] defaultJvmOpts(String... extra)
    {
        List<String> result = new ArrayList<>();
        if (MONITORED_ITEMS.contains(ConfigurableMonitor.Item.GC_LOGS))
            result.addAll(List.of("-Xlog:async", "-Xlog:gc*:file=gc.log:time,level,tags")); // -Xlog:async requires jdk 17, see https://aws.amazon.com/blogs/developer/asynchronous-logging-corretto-17/
        result.add("-XX:+UseZGC");
        if (JDK_TO_USE.contains("21"))
            result.add("-XX:+ZGenerational");
        result.add("-XX:+AlwaysPreTouch");
        if (MONITORED_ITEMS.contains(ConfigurableMonitor.Item.ASYNC_PROF_CPU) || MONITORED_ITEMS.contains(ConfigurableMonitor.Item.ASYNC_PROF_ALLOCATION))
            result.addAll(List.of("-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"));
        result.addAll(Arrays.asList(extra));
        return result.toArray(new String[0]);
    }
}
