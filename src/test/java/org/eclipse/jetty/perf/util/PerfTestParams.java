package org.eclipse.jetty.perf.util;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.Jvm;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.SimpleClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleNodeArrayConfiguration;
import org.eclipse.jetty.perf.jenkins.JenkinsToolJdk;
import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;

public class PerfTestParams implements Serializable
{
    private static final String DEFAULT_JDK_NAME = "load-jdk11";
    private static final List<String> DEFAULT_JVM_OPTS = Arrays.asList("-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints");

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

    public ClusterConfiguration getClusterConfiguration()
    {
        return new SimpleClusterConfiguration()
            .jvm(new Jvm(new JenkinsToolJdk(DEFAULT_JDK_NAME)))
            .nodeArray(new SimpleNodeArrayConfiguration("server")
                .node(new Node("1", "localhost"))
                .jvm(new Jvm(new JenkinsToolJdk(DEFAULT_JDK_NAME), jvmOpts()))
            )
            .nodeArray(new SimpleNodeArrayConfiguration("loaders")
                .node(new Node("1", "localhost"))
                .jvm(new Jvm(new JenkinsToolJdk(DEFAULT_JDK_NAME), jvmOpts()))
            )
            .nodeArray(new SimpleNodeArrayConfiguration("probe")
                .jvm(new Jvm(new JenkinsToolJdk(DEFAULT_JDK_NAME), jvmOpts()))
            );
    }

    public Duration getWarmupDuration()
    {
        return Duration.ofSeconds(10);
    }

    public Duration getRunDuration()
    {
        return Duration.ofSeconds(20);
    }

    public String getReportPath()
    {
        return formattedDate + File.separator + protocol.name();
    }

    public EnumSet<ConfigurableMonitor.Item> getMonitoredItems()
    {
        return EnumSet.of(
            ConfigurableMonitor.Item.CMDLINE_CPU,
            ConfigurableMonitor.Item.CMDLINE_MEMORY,
            ConfigurableMonitor.Item.CMDLINE_NETWORK,
            ConfigurableMonitor.Item.JHICCUP,
            ConfigurableMonitor.Item.ASYNC_PROF_CPU);
    }

    @Override
    public String toString()
    {
        return protocol.name();
    }

    private static String[] jvmOpts(String... extra)
    {
        List<String> result = new ArrayList<>(DEFAULT_JVM_OPTS);
        result.addAll(Arrays.asList(extra));
        return result.toArray(new String[0]);
    }
}
