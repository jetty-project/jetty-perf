package perf;

import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
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
import perf.monitoring.AsyncProfilerCpuMonitor;
import perf.monitoring.ConfigurableMonitor;
import perf.util.ThreadDumpNodeJob;

import static util.ReportUtil.download;
import static util.ReportUtil.xformHisto;

public class SslPerfLimitTest implements Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(SslPerfLimitTest.class);

    private static final Duration WARMUP_DURATION = Duration.ofSeconds(60);
    private static final Duration RUN_DURATION = Duration.ofMinutes(Long.getLong("test.runFor", 10));

    @Test
    public void testSslPerfLimit() throws Exception
    {
        String[] defaultJvmOpts = {
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+DebugNonSafepoints",
            "-Xlog:gc*:file=gc.log:time,level,tags",
            "-Djava.rmi.server.hostname=localhost"
        };

        String jdkName = System.getProperty("test.jdk.name", "jdk11");
        String jdkExtraArgs = System.getProperty("test.jdk.extraArgs", null);
        EnumSet<ConfigurableMonitor.Item> monitoredItems = EnumSet.of(ConfigurableMonitor.Item.CMDLINE_CPU, ConfigurableMonitor.Item.CMDLINE_MEMORY, ConfigurableMonitor.Item.CMDLINE_NETWORK);

        ClusterConfiguration cfg = new SimpleClusterConfiguration()
            .jvm(new Jvm(new JenkinsToolJdk(jdkName)))
            .nodeArray(new SimpleNodeArrayConfiguration("server")
                .jvm(new Jvm(new JenkinsToolJdk(jdkName), buildJvmOpts(defaultJvmOpts, jdkExtraArgs, "-Xmx32g", "-Xms32g")))
                .node(new Node("1", "load-master"))
            )
            .nodeArray(new SimpleNodeArrayConfiguration("loaders")
                .jvm(new Jvm(new JenkinsToolJdk(jdkName), buildJvmOpts(defaultJvmOpts, jdkExtraArgs, "-Xmx8g", "-Xms8g")))
                .node(new Node("1a", "load-1"))
                .node(new Node("1b", "load-1"))
                .node(new Node("2a", "load-2"))
                .node(new Node("2b", "load-2"))
                .node(new Node("3a", "load-3"))
                .node(new Node("3b", "load-3"))
                .node(new Node("4a", "load-4"))
                .node(new Node("4b", "load-4"))
                .node(new Node("5a", "load-5"))
                .node(new Node("5b", "load-5"))
                //.node(new Node("6", "load-6"))
                .node(new Node("7a", "load-7"))
                .node(new Node("7b", "load-7"))
                .node(new Node("8a", "load-8"))
                .node(new Node("8b", "load-8"))
            )
            .nodeArray(new SimpleNodeArrayConfiguration("probe")
                .jvm(new Jvm(new JenkinsToolJdk(jdkName), buildJvmOpts(defaultJvmOpts, jdkExtraArgs, "-Xmx8g", "-Xms8g")))
                .node(new Node("1", "load-sample"))
            )
            ;

        LOG.info("Initializing...");
        try (Cluster cluster = new Cluster(cfg))
        {
            NodeArray serverArray = cluster.nodeArray("server");
            NodeArray loadersArray = cluster.nodeArray("loaders");
            NodeArray probeArray = cluster.nodeArray("probe");
            int participantCount = cfg.nodeArrays().stream().mapToInt(na -> na.nodes().size()).sum() + 1; // + 1 b/c of the test itself
            int loadersCount = cfg.nodeArrays().stream().filter(na -> na.id().equals("loaders")).mapToInt(na -> na.nodes().size()).sum();

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
                String path = Paths.get(getClass().getResource("/keystore.p12").toURI()).toString();
                serverSslContextFactory.setKeyStorePath(path);
                serverSslContextFactory.setKeyStorePassword("storepwd");
                SslConnectionFactory ssl = new SslConnectionFactory(serverSslContextFactory, http.getProtocol());

                ServerConnector serverConnector = new ServerConnector(server, 1, 32, ssl, http);
                serverConnector.setPort(8443);

                server.addConnector(serverConnector);
                server.setHandler(new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1)));
                server.start();
            }).get(30, TimeUnit.SECONDS);

            LOG.info("Warming up...");
            URI serverUri = new URI("https://" + serverArray.hostnameOf("1") + ":8443");
            NodeArrayFuture warmupLoaders = loadersArray.executeOnAll(tools -> runLoadGenerator(serverUri, WARMUP_DURATION));
            try
            {
                warmupLoaders.get(WARMUP_DURATION.toSeconds() + 30, TimeUnit.SECONDS);
            }
            catch (TimeoutException e)
            {
                // execute sequentially
                for (String id : loadersArray.ids())
                {
                    loadersArray.executeOn(id, new ThreadDumpNodeJob()).get();
                }
                throw e;
            }

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

                    // collect a different FG for each time quantum of the loaders
                    long runQuantum = RUN_DURATION.toMillis() / loadersCount;
                    long gap = runQuantum / 5;
                    for (int i = 0; i < loadersCount; i++)
                    {
                        Thread.sleep(gap);
                        AsyncProfilerCpuMonitor cpuMonitor = new AsyncProfilerCpuMonitor("profile." + (i + 1) + ".html");
                        Thread.sleep(runQuantum - gap * 2);
                        cpuMonitor.close();
                        Thread.sleep(gap);
                    }

                    LOG.info("Server sync'ing on end barrier...");
                    tools.barrier("run-end-barrier", participantCount).await();
                    LifeCycle.stop(listener);
                    server.stop();
                }
            });

            NodeArrayFuture loadersFuture = loadersArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor m = new ConfigurableMonitor(monitoredItems))
                {
                    int index = tools.barrier("loader-index-barrier", loadersCount).await();
                    tools.barrier("run-start-barrier", participantCount).await();
                    long delayMs = RUN_DURATION.toMillis() / loadersCount * index;
                    LOG.info("Loader #{} waiting {} ms", index, delayMs);
                    Thread.sleep(delayMs);
                    runLoadGenerator(serverUri, RUN_DURATION.minus(Duration.ofMillis(delayMs)), "loader.hlog", "status.txt");
                    LOG.info("Loader #{} sync'ing on end barrier...", index);
                    tools.barrier("run-end-barrier", participantCount).await();
                }
            });

            NodeArrayFuture probeFuture = probeArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor m = new ConfigurableMonitor(monitoredItems))
                {
                    tools.barrier("run-start-barrier", participantCount).await();
                    runProbeGenerator(serverUri, RUN_DURATION, "probe.hlog", "status.txt");
                    LOG.info("Probe sync'ing on end barrier...");
                    tools.barrier("run-end-barrier", participantCount).await();
                }
            });

            // signal all participants to start
            cluster.tools().barrier("run-start-barrier", participantCount).await(30, TimeUnit.SECONDS);
            // signal all participants to stop monitoring
            LOG.info("JUnit sync'ing on end barrier...");
            cluster.tools().barrier("run-end-barrier", participantCount).await(RUN_DURATION.toSeconds() + 30, TimeUnit.SECONDS);

            // wait for all monitoring reports to be written
            serverFuture.get(30, TimeUnit.SECONDS);
            loadersFuture.get(30, TimeUnit.SECONDS);
            probeFuture.get(30, TimeUnit.SECONDS);

            LOG.info("Downloading reports...");
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

    private static String[] buildJvmOpts(String[] defaultJvmOpts, String jdkExtraArgs, String... moreArgs)
    {
        List<String> jvmOpts = new ArrayList<>();
        jvmOpts.addAll(Arrays.asList(defaultJvmOpts));
        jvmOpts.addAll(Arrays.asList(moreArgs));
        jvmOpts.addAll(jdkExtraArgs == null ? Collections.emptyList() : Arrays.asList(jdkExtraArgs.split(" ")));
        return jvmOpts.toArray(new String[0]);
    }

    private void runLoadGenerator(URI uri, Duration duration) throws Exception
    {
        runLoadGenerator(uri, duration, null, null, false);
    }

    private void runLoadGenerator(URI uri, Duration duration, String histogramFilename, String statusFilename) throws Exception
    {
        runLoadGenerator(uri, duration, histogramFilename, statusFilename, true);
    }

    private void runLoadGenerator(URI uri, Duration duration, String histogramFilename, String statusFilename, boolean rampUp) throws Exception
    {
        Scheduler scheduler = new ScheduledExecutorScheduler();
        scheduler.start();
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(uri.getScheme())
            .host(uri.getHost())
            .port(uri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(duration.toSeconds(), TimeUnit.SECONDS)
            .resourceRate(50_000)
            .threads(2)
            .resource(new Resource("/"))
            .scheduler(scheduler)
            ;

        if (rampUp)
        {
            builder.rateRampUpPeriod(20);
        }
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
        CompletionException failure = new CompletionException("", null);
        Scheduler.Task job = scheduler.schedule(() ->
        {
            String dump = loadGenerator.dump();
            failure.addSuppressed(new CompletionException(dump, null));
        }, duration.toSeconds() + 5, TimeUnit.SECONDS);
        LOG.info("load generation begin");
        try
        {
            loadGenerator.begin().get(duration.toSeconds() + 10, TimeUnit.SECONDS);
            job.cancel();
        }
        catch (Throwable e)
        {
            String dump = loadGenerator.dump();
            failure.addSuppressed(new CompletionException(dump, e));
            failure.printStackTrace();
            throw failure;
        }
        finally
        {
            scheduler.stop();
        }
        LOG.info("load generation complete");
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
        loadGenerator.begin().join();
        LOG.info("probe generation complete");
    }
}
