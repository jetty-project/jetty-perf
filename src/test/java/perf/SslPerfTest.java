package perf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.junit.jupiter.api.Test;
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
import org.mortbay.jetty.orchestrator.configuration.SshRemoteHostLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SslPerfTest implements Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(SslPerfTest.class);

    public static final int WARMUP_REQUEST_COUNT = 1_000;
    public static final int RUN_REQUEST_COUNT = 100_000;

    @Test
    public void testSslPerf() throws Exception
    {
        System.setProperty("jetty.orchestrator.skipCleanup", "false");

        ClusterConfiguration cfg = new SimpleClusterConfiguration()
            .jvm(new Jvm(new JenkinsToolJdk("jdk11")))
            .hostLauncher(new SshRemoteHostLauncher())
            .nodeArray(new SimpleNodeArrayConfiguration("server").topology(new NodeArrayTopology(
                new Node("1", "load-master")
            )))
            .nodeArray(new SimpleNodeArrayConfiguration("loaders").topology(new NodeArrayTopology(
                new Node("1", "load-1"),
                new Node("2", "load-2"),
                new Node("3", "load-3")
            )))
//            .nodeArray(new SimpleNodeArrayConfiguration("probe").topology(new NodeArrayTopology(
//                new Node("1", "load-4")
//            )))
            ;

        try (Cluster cluster = new Cluster(cfg))
        {
            LOG.info("Initializing...");
            NodeArray serverArray = cluster.nodeArray("server");
            NodeArray loadersArray = cluster.nodeArray("loaders");

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
                try (AsyncProfiler asyncProfiler = new AsyncProfiler("warmup-loader.html", ProcessHandle.current().pid()))
                {
                    runClient(RUN_REQUEST_COUNT, serverUri);
                }
            }).get();

            LOG.info("Running...");
            long before = System.nanoTime();

            // start the async profiler on the server
            NodeArrayFuture serverAsyncProfiler = serverArray.executeOnAll(tools ->
            {
                try (AsyncProfiler asyncProfiler = new AsyncProfiler("server.html", ProcessHandle.current().pid()))
                {
                    tools.barrier("server-async-profiler-barrier", 2).await();
                }
            });

            loadersArray.executeOnAll(tools ->
            {
                try (AsyncProfiler asyncProfiler = new AsyncProfiler("loader.html", ProcessHandle.current().pid()))
                {
                    runClient(RUN_REQUEST_COUNT, serverUri);
                }
            }).get();

            cluster.tools().barrier("server-async-profiler-barrier", 2).await(); // stop the async profiler on the server
            serverAsyncProfiler.get();

            // download server FG
            {
                Path serverRootPath = serverArray.rootPathOf("1");
                File reportFolder = new File("target/report/server");
                reportFolder.mkdirs();
                try (OutputStream os = new FileOutputStream(new File(reportFolder, "server.html")))
                {
                    Files.copy(serverRootPath.resolve("server.html"), os);
                }
            }

            // download loaders FGs
            for (String id : loadersArray.ids())
            {
                Path loaderRootPath = loadersArray.rootPathOf(id);
                File reportFolder = new File("target/report/loader", id);
                reportFolder.mkdirs();
                try (OutputStream os = new FileOutputStream(new File(reportFolder, "loader.html")))
                {
                    Files.copy(loaderRootPath.resolve("loader.html"), os);
                }
            }

            long after = System.nanoTime();
            LOG.info("Done; elapsed=" + TimeUnit.NANOSECONDS.toMillis(after - before) + " ms");
        }
    }

    private void runClient(int count, URI uri) throws Exception
    {
        if (Boolean.getBoolean("perf.useLoadGenerator"))
        {
            runLoadGenerator(count, uri);
            return;
        }
        long before = System.nanoTime();
        LOG.info("Running client on URI " + uri + "; " + count + " requests...");

        SslContextFactory.Client clientSslContextFactory = new SslContextFactory.Client();
        String path = getClass().getResource("/keystore.p12").getPath();
        clientSslContextFactory.setKeyStorePath(path);
        clientSslContextFactory.setKeyStorePassword("storepwd");

        ClientConnector clientConnector = new ClientConnector();
        clientConnector.setSslContextFactory(clientSslContextFactory);

        HttpClientTransportOverHTTP transport = new HttpClientTransportOverHTTP(clientConnector);
        HttpClient httpClient = new HttpClient(transport);
        httpClient.setCookieStore(new HttpCookieStore.Empty());
        httpClient.start();

        CountDownLatch latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++)
        {
            httpClient.newRequest(uri).send(r -> latch.countDown());
        }
        latch.await();

        httpClient.stop();
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - before);
        LOG.info("Stopped client; ran for " + elapsedSeconds + " seconds");
    }

    private void runLoadGenerator(int count, URI uri) throws Exception
    {
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .host(uri.getHost())
            .port(uri.getPort())
            .runFor(1, TimeUnit.MINUTES)
            .resourceRate(1)
            .resource(new Resource("/"))
            .rateRampUpPeriod(2);

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
}
