package perf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
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
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import javax.management.remote.JMXServiceURL;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogReader;
import org.HdrHistogram.HistogramLogWriter;
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
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
import perf.monitoring.AsyncProfilerCpuMonitor;
import perf.monitoring.ConfigurableMonitor;

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
        List<String> jvmOpts = new ArrayList<>(Arrays.asList(defaultJvmOpts));
        jvmOpts.addAll(jdkExtraArgs == null ? Collections.emptyList() : Arrays.asList(jdkExtraArgs.split(" ")));
        EnumSet<ConfigurableMonitor.Item> monitoredItems = EnumSet.of(ConfigurableMonitor.Item.CMDLINE_CPU, ConfigurableMonitor.Item.CMDLINE_MEMORY, ConfigurableMonitor.Item.CMDLINE_NETWORK);

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

                ServerConnector serverConnector = new ServerConnector(server, 1, 32, ssl, http);
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
                    runLoadGenerator(serverUri, RUN_DURATION.minus(Duration.ofMillis(delayMs)), "loader.hlog", "status.csv");
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
            for (int i = 0; i < loadersCount; i++)
                download(serverArray, new File("target/report/server"), "profile." + (i + 1) + ".html");
            download(serverArray, new File("target/report/server"), "server.hlog", "gc.log");
            xformHisto(serverArray, new File("target/report/server"), "server.hlog");
            download(serverArray, new File("target/report/server"), ConfigurableMonitor.defaultFilenamesOf(monitoredItems));
            // download loaders FGs & transform histograms
            download(loadersArray, new File("target/report/loader"), "loader.hlog", "status.csv", "gc.log");
            xformHisto(loadersArray, new File("target/report/loader"), "loader.hlog");
            download(loadersArray, new File("target/report/loader"), ConfigurableMonitor.defaultFilenamesOf(monitoredItems));
            // download probes FGs & transform histograms
            download(probeArray, new File("target/report/probe"), "probe.hlog", "status.csv", "gc.log");
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
            String inputFileName = new File(reportFolder, filename).getPath();

            try (HistogramLogReader reader = new HistogramLogReader(inputFileName))
            {
                Histogram total = new Histogram(3);
                while (reader.hasNext())
                {
                    Histogram histogram = (Histogram) reader.nextIntervalHistogram();
                    total.add(histogram);
                }
                try (PrintStream ps = new PrintStream(new FileOutputStream(inputFileName + ".hgrm")))
                {
                    total.outputPercentileDistribution(ps, 1000.0); // scale by 1000 to report in microseconds
                }
            }

            createHtmlHistogram(inputFileName);
        }
    }

    private void createHtmlHistogram(String inputFileName) throws IOException
    {
        String targetFilename = inputFileName + ".html";
        File inputFile = new File(inputFileName);
        String title = inputFile.getName().split("\\.")[0];

        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/graph-template.html"), StandardCharsets.UTF_8)))
        {
            while (true)
            {
                String line = r.readLine();
                if (line == null)
                    break;
                sb.append(line).append('\n');
            }
        }
        String html = sb.toString();

        StringBuilder sb2 = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8)))
        {
            while (true)
            {
                String line = reader.readLine();
                if (line == null)
                    break;

                sb2.append(line).append("\n");
            }
        }
        String histograms = sb2.toString();

        html = html.replace("##TITLE##", title);
        html = html.replace("##HISTOGRAMS##", histograms);

        try (OutputStream os = new FileOutputStream(targetFilename))
        {
            os.write(html.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static class LatencyRecordingChannelListener extends AbstractLifeCycle implements HttpChannel.Listener
    {
        private final Map<Request, Long> timestamps = new ConcurrentHashMap<>();
        private final Recorder recorder = new Recorder(3);
        private final Timer timer = new Timer();
        private final HistogramLogWriter writer;

        public LatencyRecordingChannelListener(String histogramFilename) throws FileNotFoundException
        {
            writer = new HistogramLogWriter(histogramFilename);
            long now = System.currentTimeMillis();
            writer.setBaseTime(now);
            writer.outputBaseTime(now);
            writer.outputStartTime(now);
            timer.schedule(new TimerTask()
            {
                private final Histogram h = new Histogram(3);
                @Override
                public void run()
                {
                    recorder.getIntervalHistogramInto(h);
                    writer.outputIntervalHistogram(h);
                    h.reset();
                }
            }, 1000, 1000);
        }

        @Override
        protected void doStop()
        {
            timer.cancel();
            writer.outputIntervalHistogram(recorder.getIntervalHistogram());
            writer.close();
        }

        @Override
        public void onRequestBegin(Request request)
        {
            long begin = System.nanoTime();
            timestamps.put(request, begin);
        }

        @Override
        public void onComplete(Request request)
        {
            long begin = timestamps.remove(request);
            long responseTime = System.nanoTime() - begin;
            recorder.recordValue(responseTime);
        }
    }

    private static class ResponseStatusListener implements Resource.NodeListener, LoadGenerator.CompleteListener
    {
        private final Timer timer = new Timer();
        // Array of counters:
        //  [0] unknown status [1] HTTP 1xx [2] HTTP 2xx [3] HTTP 3xx [4] HTTP 4xx [5] HTTP 5xx
        private LongAdder[] savedArray = new LongAdder[6];
        private final AtomicReference<LongAdder[]> statuses = new AtomicReference<>(new LongAdder[6]);
        private final PrintWriter printWriter;

        public ResponseStatusListener(String statusFilename) throws IOException
        {
            printWriter = new PrintWriter(statusFilename, StandardCharsets.UTF_8);
            for (int i = 0; i < savedArray.length; i++)
            {
                savedArray[i] = new LongAdder();
                statuses.get()[i] = new LongAdder();
            }
            timer.schedule(new TimerTask() {
                @Override
                public void run()
                {
                    writeStatuses();
                }
            }, 1000, 1000);
        }

        private synchronized void writeStatuses()
        {
            LongAdder[] newStatuses = savedArray;
            for (LongAdder newStatus : newStatuses)
                newStatus.reset();
            savedArray = statuses.getAndSet(newStatuses);

            for (int i = 0; i < savedArray.length; i++)
            {
                LongAdder status = savedArray[i];
                printWriter.print(status.sum());
                if (i < savedArray.length - 1)
                    printWriter.print(',');
            }
            printWriter.println();
            printWriter.flush();
        }

        @Override
        public void onResourceNode(Resource.Info info)
        {
            int status = info.getStatus();
            int idx = status / 100;
            if (idx > 5 || idx < 1)
                idx = 0;
            statuses.get()[idx].increment();
        }

        @Override
        public void onComplete(LoadGenerator loadGenerator)
        {
            timer.cancel();
            writeStatuses();
            printWriter.close();
        }
    }

    private static class ResponseTimeListener implements Resource.NodeListener, LoadGenerator.CompleteListener
    {
        private final Recorder recorder = new Recorder(3);
        private final Timer timer = new Timer();
        private final HistogramLogWriter writer;

        private ResponseTimeListener(String histogramFilename) throws FileNotFoundException
        {
            writer = new HistogramLogWriter(histogramFilename);
            long now = System.currentTimeMillis();
            writer.setBaseTime(now);
            writer.outputBaseTime(now);
            writer.outputStartTime(now);
            timer.schedule(new TimerTask()
            {
                private final Histogram h = new Histogram(3);
                @Override
                public void run()
                {
                    recorder.getIntervalHistogramInto(h);
                    writer.outputIntervalHistogram(h);
                    h.reset();
                }
            }, 1000, 1000);
        }

        @Override
        public void onResourceNode(Resource.Info info)
        {
            long responseTime = info.getResponseTime() - info.getRequestTime();
            recorder.recordValue(responseTime);
        }

        @Override
        public void onComplete(LoadGenerator generator)
        {
            timer.cancel();
            writer.outputIntervalHistogram(recorder.getIntervalHistogram());
            writer.close();
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
