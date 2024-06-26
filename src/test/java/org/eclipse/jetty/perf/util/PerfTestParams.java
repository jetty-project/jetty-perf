package org.eclipse.jetty.perf.util;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
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
        // ConfigurableMonitor.Item.ASYNC_PROF_CPU,
        ConfigurableMonitor.Item.JHICCUP,
        ConfigurableMonitor.Item.GC_LOGS
    );

    private static final SimpleClusterConfiguration CLUSTER_CONFIGURATION = new SimpleClusterConfiguration()
        .jvm(new Jvm(new LocalJdk(JDK_TO_USE)))
        .nodeArray(new SimpleNodeArrayConfiguration("server")
            .node(new Node("load-master-2"))
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

    public PerfTestParams(Protocol protocol)
    {
        this.protocol = protocol;
    }

    public Protocol getProtocol()
    {
        return protocol;
    }

    public ClusterConfiguration getClusterConfiguration()
    {
        return CLUSTER_CONFIGURATION;
    }

    public URI getServerUri() throws URISyntaxException
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

        return new URI("http" + (getProtocol().isSecure() ? "s" : "") + "://" + serverHostname + ":" + getServerPort());
    }

    public int getServerPort()
    {
        return getProtocol().isSecure() ? 9443 : 9080;
    }

    public int getParticipantCount()
    {
        return getClusterConfiguration().nodeArrays().stream().mapToInt(na -> na.nodes().size()).sum() + 1; // + 1 b/c of the test itself
    }

    public EnumSet<ConfigurableMonitor.Item> getMonitoredItems()
    {
        return MONITORED_ITEMS;
    }

    public int getLoaderRate()
    {
        // TODO rate should vary according to protocol
        // i.e.: it takes less resources to serve HTTP vs HTTPS -> rate should be higher for HTTP
        return 60_000;
    }

    public int getProbeRate()
    {
        return 100;
    }

    public long getExpectedP99ServerLatency()
    {
        switch (protocol)
        {
            case http:
                return 5_000;
            case https:
                return 6_500;
            case h2c:
                return 13_000;
            case h2:
                return 75_000;
            default:
                throw new AssertionError();
        }
    }

    public long getExpectedP99ProbeLatency()
    {
        switch (protocol)
        {
            case http:
                return 625_000;
            case https:
                return 1_000_000;
            case h2c:
                return 700_000;
            case h2:
                return 1_000_000;
            default:
                throw new AssertionError();
        }
    }

    public double getExpectedP99ErrorMargin()
    {
        switch (protocol)
        {
            case http:
                return 10.0;
            case https:
                return 10.0;
            case h2c:
                return 15.0;
            case h2:
                return 15.0;
            default:
                throw new AssertionError();
        }
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
            result.addAll(Arrays.asList("-Xlog:async", "-Xlog:gc*:file=gc.log:time,level,tags")); // -Xlog:async requires jdk 17, see https://aws.amazon.com/blogs/developer/asynchronous-logging-corretto-17/
        //result.add("-XX:+UnlockExperimentalVMOptions"); // JDK 11 needs this flag to enable ZGC
        result.add("-XX:+UseZGC");
        result.add("-XX:+AlwaysPreTouch");
        if (MONITORED_ITEMS.contains(ConfigurableMonitor.Item.ASYNC_PROF_CPU) || MONITORED_ITEMS.contains(ConfigurableMonitor.Item.ASYNC_PROF_ALLOCATION))
            result.addAll(Arrays.asList("-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"));
        result.addAll(Arrays.asList(extra));
        return result.toArray(new String[0]);
    }
}
