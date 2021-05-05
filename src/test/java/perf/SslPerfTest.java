package perf;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.Jvm;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.SimpleClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleNodeArrayConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.handler.AsyncHandler;
import perf.histogram.HgrmReport;
import perf.histogram.HtmlReport;
import perf.histogram.loader.ResponseTimeListener;
import perf.histogram.server.LatencyRecordingChannelListener;
import perf.jenkins.JenkinsToolJdk;
import perf.monitoring.ConfigurableMonitor;

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
            )
            .nodeArray(new SimpleNodeArrayConfiguration("probe")
                .node(new Node("1", "load-4"))
            )
            ;

        LOG.info("Initializing...");
        try (Cluster cluster = new Cluster(cfg))
        {
            NodeArray serverArray = cluster.nodeArray("server");
            NodeArray loadersArray = cluster.nodeArray("loaders");
            NodeArray probeArray = cluster.nodeArray("probe");
            int participantCount = cfg.nodeArrays().stream().mapToInt(na -> na.nodes().size()).sum() + 1; // + 1 b/c of the test itself

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
            }).get();

            LOG.info("Warming up...");
            URI serverUri = new URI("https://" + serverArray.hostnameOf("1") + ":8443");
            NodeArrayFuture warmupLoaders = loadersArray.executeOnAll(tools -> runLoadGenerator(serverUri, WARMUP_DURATION));
            NodeArrayFuture warmupProbe = probeArray.executeOnAll(tools -> runLoadGenerator(serverUri, WARMUP_DURATION));
            warmupLoaders.get();
            warmupProbe.get();

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
                    runLoadGenerator(serverUri, RUN_DURATION, "loader.hlog");
                    tools.barrier("run-end-barrier", participantCount).await();
                }
            });

            NodeArrayFuture probeFuture = probeArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor m = new ConfigurableMonitor(monitoredItems))
                {
                    tools.barrier("run-start-barrier", participantCount).await();
                    runProbeGenerator(serverUri, RUN_DURATION, "probe.hlog");
                    tools.barrier("run-end-barrier", participantCount).await();
                }
            });

            cluster.tools().barrier("run-start-barrier", participantCount).await(); // signal all participants to start
            cluster.tools().barrier("run-end-barrier", participantCount).await(); // signal all participants to stop monitoring

            // wait for all monitoring reports to be written
            serverFuture.get();
            loadersFuture.get();
            probeFuture.get();

            // download servers FGs & transform histograms
            download(serverArray, new File("target/report/server"), "server.hlog", "gc.log");
            xformHisto(serverArray, new File("target/report/server"), "server.hlog");
            download(serverArray, new File("target/report/server"), ConfigurableMonitor.defaultFilenamesOf(monitoredItems));
            // download loaders FGs & transform histograms
            download(loadersArray, new File("target/report/loader"), "loader.hlog", "gc.log");
            xformHisto(loadersArray, new File("target/report/loader"), "loader.hlog");
            download(loadersArray, new File("target/report/loader"), ConfigurableMonitor.defaultFilenamesOf(monitoredItems));
            // download probes FGs & transform histograms
            download(probeArray, new File("target/report/probe"), "probe.hlog", "gc.log");
            xformHisto(probeArray, new File("target/report/probe"), "probe.hlog");
            download(probeArray, new File("target/report/probe"), ConfigurableMonitor.defaultFilenamesOf(monitoredItems));

            long after = System.nanoTime();
            LOG.info("Done; elapsed=" + TimeUnit.NANOSECONDS.toMillis(after - before) + " ms");
        }
    }

    private void download(NodeArray nodeArray, File targetFolder, String... filenames) throws IOException
    {
        download(nodeArray, targetFolder, Arrays.asList(filenames));
    }

    private void download(NodeArray nodeArray, File targetFolder, List<String> filenames) throws IOException
    {
        for (String id : nodeArray.ids())
        {
            Path loaderRootPath = nodeArray.rootPathOf(id);
            File reportFolder = new File(targetFolder, id);
            reportFolder.mkdirs();
            for (String filename : filenames)
            {
                try (OutputStream os = new FileOutputStream(new File(reportFolder, filename)))
                {
                    Files.copy(loaderRootPath.resolve(filename), os);
                }
            }
        }
    }

    private void xformHisto(NodeArray nodeArray, File targetFolder, String filename) throws IOException
    {
        for (String id : nodeArray.ids())
        {
            File reportFolder = new File(targetFolder, id);
            File hlogFile = new File(reportFolder, filename);

            try (OutputStream os = new FileOutputStream(new File(reportFolder, hlogFile.getName() + ".hgrm")))
            {
                HgrmReport.createHgrmHistogram(hlogFile, os);
            }
            try (OutputStream os = new FileOutputStream(new File(reportFolder, hlogFile.getName() + ".html")))
            {
                HtmlReport.createHtmlHistogram(hlogFile.getName().split("\\.")[0], hlogFile, os);
            }
        }
    }

    private void runLoadGenerator(URI uri, Duration duration) throws FileNotFoundException
    {
        runLoadGenerator(uri, duration, null);
    }

    private void runLoadGenerator(URI uri, Duration duration, String histogramFilename) throws FileNotFoundException
    {
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(uri.getScheme())
            .host(uri.getHost())
            .port(uri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(duration.toSeconds(), TimeUnit.SECONDS)
            .threads(2)
            .resourceRate(0)
            .resource(new Resource("/"))
            .rateRampUpPeriod(0);

        if (histogramFilename != null)
        {
            ResponseTimeListener responseTimeListener = new ResponseTimeListener(histogramFilename);
            builder.resourceListener(responseTimeListener);
        }

        LoadGenerator loadGenerator = builder.build();
        LOG.info("load generator config: {}", loadGenerator);
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

    private void runProbeGenerator(URI uri, Duration duration, String histogramFilename) throws FileNotFoundException
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
            .rateRampUpPeriod(0);

        LoadGenerator loadGenerator = builder.build();
        LOG.info("probe generator config: {}", loadGenerator);
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
