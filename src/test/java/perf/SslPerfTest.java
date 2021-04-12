package perf;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;
import org.terracotta.angela.client.ClientArray;
import org.terracotta.angela.client.ClientArrayFuture;
import org.terracotta.angela.client.ClusterFactory;
import org.terracotta.angela.client.config.ConfigurationContext;
import org.terracotta.angela.common.TerracottaCommandLineEnvironment;
import org.terracotta.angela.common.topology.ClientArrayTopology;

import static org.terracotta.angela.client.config.custom.CustomMultiConfigurationContext.customMultiConfigurationContext;
import static org.terracotta.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;

public class SslPerfTest
{
    public static final File ASYNC_PROFILER_HOME = new File("/work/tools/async-profiler/2.0");

    public static final int WARMUP_REQUEST_COUNT = 1_500_000;
    public static final int RUN_REQUEST_COUNT = 3_000_000;

    @Test
    public void testSslPerf() throws Exception
    {
        System.setProperty("angela.rootDir", System.getProperty("user.home") + "/.angela");
        System.setProperty("angela.java.version", "1.11");

        TerracottaCommandLineEnvironment env = TerracottaCommandLineEnvironment.DEFAULT.withJavaOpts("-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints");
        ConfigurationContext configContext = customMultiConfigurationContext()
            .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().host("localhost"))).terracottaCommandLineEnvironment(env))
            .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().host("localhost"))).terracottaCommandLineEnvironment(env))
            ;

        try (ClusterFactory factory = new ClusterFactory("SslPerfTest::sslPerf", configContext))
        {
            System.out.println("Initializing...");
            ClientArray serverArray = factory.clientArray();
            ClientArray clientArray = factory.clientArray();

            serverArray.executeOnAll(cluster ->
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

                cluster.atomicCounter("server-pid", ProcessHandle.current().pid());

            }).get();
            long serverPid = factory.cluster().atomicCounter("server-pid", 0L).get();

            System.out.println("Warming up...");
            clientArray.executeOnAll(cluster -> runClient(WARMUP_REQUEST_COUNT, "https://localhost:8443")).get();

            System.out.println("Running...");
            long before = System.nanoTime();
            ClientArrayFuture clientArrayFuture = clientArray.executeOnAll(cluster ->
            {
                cluster.atomicCounter("client-pid", ProcessHandle.current().pid());
                cluster.barrier("client-barrier", 2).await();
                cluster.barrier("client-barrier", 2).await();

                runClient(RUN_REQUEST_COUNT, "https://localhost:8443");
            });

            factory.cluster().barrier("client-barrier", 2).await();
            long clientPid = factory.cluster().atomicCounter("client-pid", 0L).get();
            spawnAsyncProfiler("target/server.html", serverPid);
            spawnAsyncProfiler("target/client.html", clientPid);
            factory.cluster().barrier("client-barrier", 2).await();
            clientArrayFuture.get();

            long after = System.nanoTime();
            System.out.println("Done; elapsed=" + TimeUnit.NANOSECONDS.toMillis(after - before));
        }
    }

    private void runClient(int count, String urlAsString) throws Exception
    {
        long before = System.nanoTime();
        System.out.println("Running client; " + count + " requests...");
        URI uri = new URI(urlAsString);

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
        ExecutorService executorService = Executors.newFixedThreadPool(8);

        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            Future<Object> f = executorService.submit(() ->
            {
                ContentResponse response = httpClient.GET(uri);
                return null;
            });
            futures.add(f);
        }

        for (Future<Object> future : futures)
        {
            future.get();
        }

        executorService.shutdownNow();
        httpClient.stop();
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - before);
        System.out.println("Stopped client; ran for " + elapsedSeconds + " seconds");
    }

    private static void spawnAsyncProfiler(String fgFilename, long pid) throws IOException
    {
        File fgFile = new File(fgFilename);
        new ProcessBuilder("./profiler.sh", "-d", "3600", "-f", fgFile.getAbsolutePath(), Long.toString(pid))
            .directory(ASYNC_PROFILER_HOME)
            .redirectErrorStream(true)
            .start();
    }
}
