package perf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.HttpConnectionFactory;
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
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayTopology;
import org.mortbay.jetty.orchestrator.configuration.SimpleClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleNodeArrayConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SshRemoteHostLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SslPerfTest implements Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(SslPerfTest.class);

    private static final Duration WARMUP_DURATION = Duration.ofSeconds(10);
    private static final Duration RUN_DURATION = Duration.ofSeconds(20);

    @Test
    public void testSslPerf() throws Exception
    {
        System.setProperty("jetty.orchestrator.skipCleanup", "false");

        ClusterConfiguration cfg = new SimpleClusterConfiguration()
            //.jvm(new Jvm(new JenkinsToolJdk("jdk11")))
            .hostLauncher(new SshRemoteHostLauncher())
            .nodeArray(new SimpleNodeArrayConfiguration("server").topology(new NodeArrayTopology(
                new Node("1", "localhost")
            )))
            .nodeArray(new SimpleNodeArrayConfiguration("loaders").topology(new NodeArrayTopology(
                new Node("1", "localhost"),
                new Node("2", "localhost"),
                new Node("3", "localhost")
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

                HttpConnectionFactory http = new HttpConnectionFactory();

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
            loadersArray.executeOnAll(tools ->
            {
                runLoadGenerator(serverUri, WARMUP_DURATION);
            }).get();

            LOG.info("Running...");
            long before = System.nanoTime();

            // start the async profiler on the server
            NodeArrayFuture serverAsyncProfiler = serverArray.executeOnAll(tools ->
            {
                try (AsyncProfiler asyncProfiler = new AsyncProfiler("server.html", ProcessHandle.current().pid()))
                {
                    tools.barrier("async-profiler-barrier", participantCount).await();
                }
                tools.barrier("async-profiler-barrier", participantCount).await();
            });

            loadersArray.executeOnAll(tools ->
            {
                try (AsyncProfiler asyncProfiler = new AsyncProfiler("loader.html", ProcessHandle.current().pid()))
                {
                    tools.barrier("async-profiler-barrier", participantCount).await();
                    runLoadGenerator(serverUri, RUN_DURATION);
                }
                tools.barrier("async-profiler-barrier", participantCount).await();
            });
            
            probeArray.executeOnAll(tools ->
            {
                try (AsyncProfiler asyncProfiler = new AsyncProfiler("probe.html", ProcessHandle.current().pid()))
                {
                    tools.barrier("async-profiler-barrier", participantCount).await();
                    runProbeGenerator(serverUri, RUN_DURATION);
                }
                tools.barrier("async-profiler-barrier", participantCount).await();
            });

            cluster.tools().barrier("async-profiler-barrier", participantCount).await(); // signal all participants to start
            cluster.tools().barrier("async-profiler-barrier", participantCount).await(); // wait for all participants to be done

            // download servers FGs
            download(loadersArray, new File("target/report/server"), "server.html");
            // download loaders FGs
            download(loadersArray, new File("target/report/loader"), "loader.html");
            // download probes FGs
            download(loadersArray, new File("target/report/probe"), "probe.html");

            long after = System.nanoTime();
            LOG.info("Done; elapsed=" + TimeUnit.NANOSECONDS.toMillis(after - before) + " ms");
        }
    }

    private void download(NodeArray nodeArray, File targetFolder, String filename) throws IOException
    {
        for (String id : nodeArray.ids())
        {
            Path loaderRootPath = nodeArray.rootPathOf(id);
            File reportFolder = new File(targetFolder, id);
            reportFolder.mkdirs();
            try (OutputStream os = new FileOutputStream(new File(reportFolder, filename)))
            {
                Files.copy(loaderRootPath.resolve(filename), os);
            }
        }
    }

    private void runLoadGenerator(URI uri, Duration duration)
    {
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(uri.getScheme())
            .host(uri.getHost())
            .port(uri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(duration.toSeconds(), TimeUnit.SECONDS)
            .resourceRate(0)
            .resource(new Resource("/"))
            .rateRampUpPeriod(5);

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
    
    private void runProbeGenerator(URI uri, Duration duration)
    {
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(uri.getScheme())
            .host(uri.getHost())
            .port(uri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(duration.toSeconds(), TimeUnit.SECONDS)
            .resourceRate(5)
            .resource(new Resource("/"))
            .rateRampUpPeriod(5);

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
