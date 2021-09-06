package org.eclipse.jetty.perf.util;

import java.io.File;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import org.eclipse.jetty.perf.jenkins.JenkinsToolJdk;
import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.Jvm;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleNodeArrayConfiguration;

public class PerfTestParams implements Serializable
{
    private static final String JDK_TO_USE = System.getProperty("test.jdk.name", "load-jdk11");

    private static final SimpleClusterConfiguration CLUSTER_CONFIGURATION = new SimpleClusterConfiguration()
        .jvm(new Jvm(new JenkinsToolJdk(JDK_TO_USE)))
        .nodeArray(new SimpleNodeArrayConfiguration("server")
            .node(new Node("load-master"))
            .jvm(new Jvm(new JenkinsToolJdk(JDK_TO_USE), defaultJvmOpts("-Xms32g", "-Xmx32g")))
        )
        .nodeArray(new SimpleNodeArrayConfiguration("loaders")
            .node(new Node("load-1"))
            .node(new Node("load-2"))
            .node(new Node("load-3"))
            .node(new Node("load-4"))
            .jvm(new Jvm(new JenkinsToolJdk(JDK_TO_USE), defaultJvmOpts("-Xms8g", "-Xmx8g")))
        )
        .nodeArray(new SimpleNodeArrayConfiguration("probe")
            .node(new Node("load-sample"))
            .jvm(new Jvm(new JenkinsToolJdk(JDK_TO_USE), defaultJvmOpts("-Xms8g", "-Xmx8g")))
        );

    private static final Duration WARMUP_DURATION = Duration.ofSeconds(60);
    private static final Duration RUN_DURATION = Duration.ofSeconds(180);

    private static final EnumSet<ConfigurableMonitor.Item> MONITORED_ITEMS = EnumSet.of(
        ConfigurableMonitor.Item.CMDLINE_CPU,
        ConfigurableMonitor.Item.CMDLINE_MEMORY,
        ConfigurableMonitor.Item.CMDLINE_NETWORK,
        ConfigurableMonitor.Item.ASYNC_PROF_CPU,
        ConfigurableMonitor.Item.JHICCUP
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
    private final String formattedDate;

    public PerfTestParams(long now, Protocol protocol)
    {
        this.formattedDate = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(now));
        this.protocol = protocol;
    }

    public Protocol getProtocol()
    {
        return protocol;
    }

    public Duration getTotalDuration()
    {
        return WARMUP_DURATION.plus(RUN_DURATION).plus(Duration.ofSeconds(10));
    }

    public Duration getWarmupDuration()
    {
        return WARMUP_DURATION;
    }

    public Duration getRunDuration()
    {
        return RUN_DURATION;
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
        return getProtocol().isSecure() ? 8443 : 8080;
    }

    public int getParticipantCount()
    {
        return getClusterConfiguration().nodeArrays().stream().mapToInt(na -> na.nodes().size()).sum() + 1; // + 1 b/c of the test itself
    }

    public String getReportPath()
    {
        return formattedDate + File.separator + protocol.name();
    }

    public EnumSet<ConfigurableMonitor.Item> getMonitoredItems()
    {
        return MONITORED_ITEMS;
    }

    @Override
    public String toString()
    {
        return protocol.name();
    }

    private static String[] defaultJvmOpts(String... extra)
    {
        List<String> result = new ArrayList<>(Arrays.asList("-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"));
        result.addAll(Arrays.asList(extra));
        return result.toArray(new String[0]);
    }
}
