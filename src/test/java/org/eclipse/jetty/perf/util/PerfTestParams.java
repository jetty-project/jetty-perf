package org.eclipse.jetty.perf.util;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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

    public enum HttpVersion
    {
        HTTP11, HTTP2
    }

    private final HttpVersion httpVersion;
    private final boolean secure;

    public PerfTestParams(HttpVersion httpVersion, boolean secure)
    {
        this.httpVersion = httpVersion;
        this.secure = secure;
    }

    public HttpVersion getHttpVersion()
    {
        return httpVersion;
    }

    public boolean isSecure()
    {
        return secure;
    }

    public ClusterConfiguration getClusterConfiguration()
    {
        return new SimpleClusterConfiguration()
            .jvm(new Jvm(new JenkinsToolJdk(DEFAULT_JDK_NAME)))
            .nodeArray(new SimpleNodeArrayConfiguration("server")
                .node(new Node("1", "load-master"))
                .jvm(new Jvm(new JenkinsToolJdk(DEFAULT_JDK_NAME), jvmOpts("-Xms32g", "-Xmx32g")))
            )
            .nodeArray(new SimpleNodeArrayConfiguration("loaders")
                .node(new Node("1", "load-1"))
                .node(new Node("2", "load-2"))
                .node(new Node("3", "load-3"))
                .node(new Node("4", "load-4"))
                .jvm(new Jvm(new JenkinsToolJdk(DEFAULT_JDK_NAME), jvmOpts("-Xms8g", "-Xmx8g")))
            )
            .nodeArray(new SimpleNodeArrayConfiguration("probe")
                .node(new Node("1", "load-sample"))
                .jvm(new Jvm(new JenkinsToolJdk(DEFAULT_JDK_NAME), jvmOpts("-Xms8g", "-Xmx8g")))
            );
    }

    public Duration getWarmupDuration()
    {
        return Duration.ofMinutes(1);
    }

    public Duration getRunDuration()
    {
        return Duration.ofMinutes(10);
    }

    public String getReportPath()
    {
        if (httpVersion == HttpVersion.HTTP11)
            if (!secure)
                return "http";
            else
                return "https";
        else if (httpVersion == HttpVersion.HTTP2)
            if (!secure)
                return "h2c";
            else
                return "h2";
        else
            throw new IllegalArgumentException("Unknown HTTP version: " + httpVersion);
    }

    public EnumSet<ConfigurableMonitor.Item> getMonitoredItems()
    {
        return EnumSet.of(ConfigurableMonitor.Item.CMDLINE_CPU,
            ConfigurableMonitor.Item.CMDLINE_MEMORY,
            ConfigurableMonitor.Item.CMDLINE_NETWORK,
            ConfigurableMonitor.Item.APROF_CPU);
    }

    @Override
    public String toString()
    {
        return getReportPath();
    }

    private static String[] jvmOpts(String... extra)
    {
        List<String> result = new ArrayList<>(DEFAULT_JVM_OPTS);
        result.addAll(Arrays.asList(extra));
        return result.toArray(new String[0]);
    }
}
