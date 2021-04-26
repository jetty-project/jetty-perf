package perf;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogReader;
import org.HdrHistogram.HistogramLogWriter;
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
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
import org.mortbay.jetty.orchestrator.configuration.NodeArrayTopology;
import org.mortbay.jetty.orchestrator.configuration.SimpleClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleNodeArrayConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SslPerfTest implements Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(SslPerfTest.class);

    private static final Duration WARMUP_DURATION = Duration.ofSeconds(60);
    private static final Duration RUN_DURATION = Duration.ofSeconds(180);

    @Test
    public void testSslPerf() throws Exception
    {
        ClusterConfiguration cfg = new SimpleClusterConfiguration()
            .jvm(new Jvm(new JenkinsToolJdk("jdk11"), "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"))
            .nodeArray(new SimpleNodeArrayConfiguration("server").topology(new NodeArrayTopology(
                new Node("1", "load-master")
            )))
            .nodeArray(new SimpleNodeArrayConfiguration("loaders").topology(new NodeArrayTopology(
                new Node("1", "load-1"),
                new Node("2", "load-2"),
                new Node("3", "load-3")
            )))
            .nodeArray(new SimpleNodeArrayConfiguration("probe").topology(new NodeArrayTopology(
                new Node("1", "load-4")
            )))
            ;

        try (Cluster cluster = new Cluster(cfg))
        {
            LOG.info("Initializing...");
            NodeArray serverArray = cluster.nodeArray("server");
            NodeArray loadersArray = cluster.nodeArray("loaders");
            NodeArray probeArray = cluster.nodeArray("probe");
            int participantCount = cfg.nodeArrays().stream().mapToInt(na -> na.topology().nodes().size()).sum() + 1; // + 1 b/c of the test itself

            serverArray.executeOnAll(tools ->
            {
                Server server = new Server();

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
                try (AsyncProfiler asyncProfiler = new AsyncProfiler("server.html"); CpuMonitor cpuMonitor = new CpuMonitor("cpu.txt"))
                {
                    tools.barrier("run-start-barrier", participantCount).await();
                    tools.barrier("run-end-barrier", participantCount).await();
                }
            });

            NodeArrayFuture loadersFuture = loadersArray.executeOnAll(tools ->
            {
                try (AsyncProfiler asyncProfiler = new AsyncProfiler("loader.html"); CpuMonitor cpuMonitor = new CpuMonitor("cpu.txt"))
                {
                    tools.barrier("run-start-barrier", participantCount).await();
                    runLoadGenerator(serverUri, RUN_DURATION, "loader.dat");
                    tools.barrier("run-end-barrier", participantCount).await();
                }
            });

            NodeArrayFuture probeFuture = probeArray.executeOnAll(tools ->
            {
                try (AsyncProfiler asyncProfiler = new AsyncProfiler("probe.html"); CpuMonitor cpuMonitor = new CpuMonitor("cpu.txt"))
                {
                    tools.barrier("run-start-barrier", participantCount).await();
                    runProbeGenerator(serverUri, RUN_DURATION, "probe.dat");
                    tools.barrier("run-end-barrier", participantCount).await();
                }
            });

            cluster.tools().barrier("run-start-barrier", participantCount).await(); // signal all participants to start
            cluster.tools().barrier("run-end-barrier", participantCount).await(); // signal all participants that profiling can be stopped

            // wait for all async profiler reports to be written
            serverFuture.get();
            loadersFuture.get();
            probeFuture.get();

            // download servers FGs
            download(serverArray, new File("target/report/server"), "server.html", "cpu.txt");
            // download loaders FGs & transform histograms
            download(loadersArray, new File("target/report/loader"), "loader.html", "loader.dat", "cpu.txt");
            xformHisto(loadersArray, new File("target/report/loader"), "loader.dat");
            // download probes FGs & transform histograms
            download(probeArray, new File("target/report/probe"), "probe.html", "probe.dat", "cpu.txt");
            xformHisto(probeArray, new File("target/report/probe"), "probe.dat");

            long after = System.nanoTime();
            LOG.info("Done; elapsed=" + TimeUnit.NANOSECONDS.toMillis(after - before) + " ms");
        }
    }

    private void download(NodeArray nodeArray, File targetFolder, String... filenames) throws IOException
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

            HistogramLogReader reader = new HistogramLogReader(inputFileName);
            Histogram total = new Histogram(3);
            while (reader.hasNext())
            {
                total.add((AbstractHistogram) reader.nextIntervalHistogram());
            }
            reader.close();

            try (PrintStream ps = new PrintStream(new FileOutputStream(inputFileName + ".hgrm")))
            {
                total.outputPercentileDistribution(ps, 1000.0);
            }
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
            writer.setBaseTime(System.currentTimeMillis());
            writer.outputBaseTime(System.currentTimeMillis());
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
