package perf;

import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.management.remote.JMXServiceURL;

import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.NodeArray;
import org.mortbay.jetty.orchestrator.NodeArrayFuture;
import org.mortbay.jetty.orchestrator.NodeJob;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.Jvm;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.SimpleClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleNodeArrayConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.handler.AsyncHandler;
import perf.histogram.loader.ResponseStatusListener;
import perf.histogram.loader.ResponseTimeListener;
import perf.histogram.server.LatencyRecordingChannelListener;
import perf.jenkins.JenkinsToolJdk;
import perf.monitoring.ConfigurableMonitor;

import static util.ReportUtil.download;
import static util.ReportUtil.xformHisto;

public class SslPerfTest implements Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(SslPerfTest.class);

    private static final Duration WARMUP_DURATION = Duration.ofSeconds(60);
    private static final Duration RUN_DURATION = Duration.ofMinutes(Long.getLong("test.runFor", 10));

    @Test
    public void testSslPerf() throws Exception
    {
        String[] defaultJvmOpts = {
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+DebugNonSafepoints",
            "-Xlog:gc*:file=gc.log:time,level,tags",
            "-Djava.rmi.server.hostname=localhost"
        };

        String jdkName = System.getProperty("test.jdk.name", "jdk11");
        String jdkExtraArgs = System.getProperty("test.jdk.extraArgs", null);
        List<String> jvmOpts = new ArrayList<>(Arrays.asList(defaultJvmOpts));
        jvmOpts.addAll(jdkExtraArgs == null ? Collections.emptyList() : Arrays.asList(jdkExtraArgs.split(" ")));
        EnumSet<ConfigurableMonitor.Item> monitoredItems = EnumSet.of(ConfigurableMonitor.Item.CMDLINE_CPU, ConfigurableMonitor.Item.CMDLINE_MEMORY, ConfigurableMonitor.Item.CMDLINE_NETWORK, ConfigurableMonitor.Item.APROF_CPU);

        ClusterConfiguration cfg = new SimpleClusterConfiguration()
            .jvm(new Jvm(new JenkinsToolJdk(jdkName), jvmOpts.toArray(new String[0])))
            .nodeArray(new SimpleNodeArrayConfiguration("server")
                .node(new Node("1", "load-master"))
            )
            .nodeArray(new SimpleNodeArrayConfiguration("loaders")
                .node(new Node("1", "load-1"))
                .node(new Node("2", "load-2"))
                .node(new Node("3", "load-3"))
                .node(new Node("4", "load-4"))
            )
            .nodeArray(new SimpleNodeArrayConfiguration("probe")
                .node(new Node("1", "zwerg"))
            )
            ;

        LOG.info("Initializing...");
        try (Cluster cluster = new Cluster(cfg))
        {
            NodeArray serverArray = cluster.nodeArray("server");
            NodeArray loadersArray = cluster.nodeArray("loaders");
            NodeArray probeArray = cluster.nodeArray("probe");
            int participantCount = cfg.nodeArrays().stream().mapToInt(na -> na.nodes().size()).sum() + 1; // + 1 b/c of the test itself

            NodeJob logSystemProps = tools -> LOG.info("JVM version: '{}', OS name: '{}', OS arch: '{}'", System.getProperty("java.vm.version"), System.getProperty("os.name"), System.getProperty("os.arch"));
            serverArray.executeOnAll(logSystemProps).get();
            loadersArray.executeOnAll(logSystemProps).get();
            probeArray.executeOnAll(logSystemProps).get();

            serverArray.executeOnAll(tools ->
            {
                Server server = new Server();
                tools.nodeEnvironment().put(Server.class.getName(), server);

                MBeanContainer mbContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
                server.addBean(mbContainer);
                server.addBean(LoggerFactory.getILoggerFactory());

                ConnectorServer connectorServer = new ConnectorServer(new JMXServiceURL("service:jmx:rmi://localhost:1099/jndi/rmi://localhost:1099/jmxrmi"), "org.eclipse.jetty:name=rmiconnectorserver");
                server.addBean(connectorServer);

                HttpConfiguration httpConfiguration = new HttpConfiguration();
                SecureRequestCustomizer customizer = new SecureRequestCustomizer();
                customizer.setSniHostCheck(false);
                httpConfiguration.addCustomizer(customizer);
                HttpConnectionFactory http = new HttpConnectionFactory(httpConfiguration);

                SslContextFactory.Server serverSslContextFactory = new SslContextFactory.Server();
                String path = getClass().getResource("/keystore.p12").getPath();
                serverSslContextFactory.setKeyStorePath(path);
                serverSslContextFactory.setKeyStorePassword("storepwd");
                SslConnectionFactory ssl = new SslConnectionFactory(serverSslContextFactory, http.getProtocol());

                ServerConnector serverConnector = new ServerConnector(server, ssl, http);
                serverConnector.setPort(8443);
                server.addConnector(serverConnector);
                server.setHandler(new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1)));
                server.start();
            }).get(30, TimeUnit.SECONDS);

            LOG.info("Warming up...");
            URI serverUri = new URI("https://" + serverArray.hostnameOf("1") + ":8443");
            NodeArrayFuture warmupLoaders = loadersArray.executeOnAll(tools -> runLoadGenerator(serverUri, WARMUP_DURATION));
            NodeArrayFuture warmupProbe = probeArray.executeOnAll(tools -> runLoadGenerator(serverUri, WARMUP_DURATION));
            warmupLoaders.get(WARMUP_DURATION.toSeconds() + 30, TimeUnit.SECONDS);
            warmupProbe.get(WARMUP_DURATION.toSeconds() + 30, TimeUnit.SECONDS);

            LOG.info("Running...");
            long before = System.nanoTime();

            NodeArrayFuture serverFuture = serverArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor m = new ConfigurableMonitor(monitoredItems))
                {
                    Server server = (Server)tools.nodeEnvironment().get(Server.class.getName());
                    Connector serverConnector = server.getConnectors()[0];
                    LatencyRecordingChannelListener listener = new LatencyRecordingChannelListener("server.hlog");
                    LifeCycle.start(listener);
                    serverConnector.addBean(listener);
                    tools.barrier("run-start-barrier", participantCount).await();
                    tools.barrier("run-end-barrier", participantCount).await();
                    LifeCycle.stop(listener);
                    server.stop();
                }
            });

            NodeArrayFuture loadersFuture = loadersArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor m = new ConfigurableMonitor(monitoredItems))
                {
                    tools.barrier("run-start-barrier", participantCount).await();
                    runLoadGenerator(serverUri, RUN_DURATION, "loader.hlog", "status.csv");
                    tools.barrier("run-end-barrier", participantCount).await();
                }
            });

            NodeArrayFuture probeFuture = probeArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor m = new ConfigurableMonitor(monitoredItems))
                {
                    tools.barrier("run-start-barrier", participantCount).await();
                    runProbeGenerator(serverUri, RUN_DURATION, "probe.hlog", "status.csv");
                    tools.barrier("run-end-barrier", participantCount).await();
                }
            });

            // signal all participants to start
            cluster.tools().barrier("run-start-barrier", participantCount).await(30, TimeUnit.SECONDS);
            // signal all participants to stop monitoring
            cluster.tools().barrier("run-end-barrier", participantCount).await(RUN_DURATION.toSeconds() + 30, TimeUnit.SECONDS);

            // wait for all monitoring reports to be written
            serverFuture.get(30, TimeUnit.SECONDS);
            loadersFuture.get(30, TimeUnit.SECONDS);
            probeFuture.get(30, TimeUnit.SECONDS);

            // download servers FGs & transform histograms
            download(serverArray, FileSystems.getDefault().getPath("target/report/server"));
            xformHisto(serverArray, FileSystems.getDefault().getPath("target/report/server"), "server.hlog");
            // download loaders FGs & transform histograms
            download(loadersArray, FileSystems.getDefault().getPath("target/report/loader"));
            xformHisto(loadersArray, FileSystems.getDefault().getPath("target/report/loader"), "loader.hlog");
            // download probes FGs & transform histograms
            download(probeArray, FileSystems.getDefault().getPath("target/report/probe"));
            xformHisto(probeArray, FileSystems.getDefault().getPath("target/report/probe"), "probe.hlog");

            long after = System.nanoTime();
            LOG.info("Done; elapsed={} ms", TimeUnit.NANOSECONDS.toMillis(after - before));
        }
    }

    private void runLoadGenerator(URI uri, Duration duration) throws IOException
    {
        runLoadGenerator(uri, duration, null, null);
    }

    private void runLoadGenerator(URI uri, Duration duration, String histogramFilename, String statusFilename) throws IOException
    {
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(uri.getScheme())
            .host(uri.getHost())
            .port(uri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(duration.toSeconds(), TimeUnit.SECONDS)
            .threads(3)
            .resourceRate(0)
            .resource(new Resource("/"));

        if (histogramFilename != null)
        {
            ResponseTimeListener responseTimeListener = new ResponseTimeListener(histogramFilename);
            builder.resourceListener(responseTimeListener);
            builder.listener(responseTimeListener);
        }
        if (statusFilename != null)
        {
            ResponseStatusListener responseStatusListener = new ResponseStatusListener(statusFilename);
            builder.resourceListener(responseStatusListener);
            builder.listener(responseStatusListener);
        }

        LoadGenerator loadGenerator = builder.build();
        LOG.info("load generation begin");
        CompletableFuture<Void> cf = loadGenerator.begin();
        cf.whenComplete((x, f) -> {
            if (f == null)
            {
                LOG.info("load generation complete");
            }
            else
            {
                LOG.info("load generation failure", f);
            }
        }).join();
    }

    private void runProbeGenerator(URI uri, Duration duration, String histogramFilename, String statusFilename) throws IOException
    {
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(uri.getScheme())
            .host(uri.getHost())
            .port(uri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(duration.toSeconds(), TimeUnit.SECONDS)
            .resourceRate(5)
            .resource(new Resource("/"))
            .resourceListener(new ResponseTimeListener(histogramFilename))
            .resourceListener(new ResponseStatusListener(statusFilename))
            .rateRampUpPeriod(0);

        LoadGenerator loadGenerator = builder.build();
        LOG.info("probe generation begin");
        CompletableFuture<Void> cf = loadGenerator.begin();
        cf.whenComplete((x, f) -> {
            if (f == null)
            {
                LOG.info("probe generation complete");
            }
            else
            {
                LOG.info("probe generation failure", f);
            }
        }).join();
    }
}
