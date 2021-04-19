package perf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.logging.AbstractLogger;
import org.codehaus.plexus.logging.Logger;
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
import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.NodeArray;
import org.mortbay.jetty.orchestrator.NodeArrayFuture;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.Jvm;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayTopology;
import org.mortbay.jetty.orchestrator.configuration.SimpleClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleNodeArrayConfiguration;
import org.mortbay.jetty.orchestrator.util.IOUtil;

public class SslPerfTest implements Serializable
{
    public static final int WARMUP_REQUEST_COUNT = 1_500_000;
    public static final int RUN_REQUEST_COUNT = 3_000_000;

    @Test
    public void testSslPerf() throws Exception
    {
        installAsyncProfilerIfNeeded();

        ClusterConfiguration cfg = new SimpleClusterConfiguration()
            .jvm(new Jvm(() -> "java", "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"))
            .nodeArray(new SimpleNodeArrayConfiguration("server-array").topology(new NodeArrayTopology(new Node("1", "localhost"))))
            .nodeArray(new SimpleNodeArrayConfiguration("client-array").topology(new NodeArrayTopology(new Node("1", "localhost"))))
            ;

        try (Cluster cluster = new Cluster("NewSslPerfTest::sslPerf", cfg))
        {
            System.out.println("Initializing...");
            NodeArray serverArray = cluster.nodeArray("server-array");
            NodeArray clientArray = cluster.nodeArray("client-array");

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

                tools.atomicCounter("server-pid", ProcessHandle.current().pid());
            }).get();
            long serverPid = cluster.tools().atomicCounter("server-pid", 0L).get();

            System.out.println("Warming up...");
            clientArray.executeOnAll(tools -> runClient(WARMUP_REQUEST_COUNT, "https://localhost:8443")).get();

            System.out.println("Running...");
            long before = System.nanoTime();
            NodeArrayFuture clientArrayFuture = clientArray.executeOnAll(tools ->
            {
                tools.atomicCounter("client-pid", ProcessHandle.current().pid());
                tools.barrier("client-barrier", 2).await();
                tools.barrier("client-barrier", 2).await();

                runClient(RUN_REQUEST_COUNT, "https://localhost:8443");
            });

            cluster.tools().barrier("client-barrier", 2).await();
            long clientPid = cluster.tools().atomicCounter("client-pid", 0L).get();
            spawnAsyncProfiler("target/server.html", serverPid);
            spawnAsyncProfiler("target/client.html", clientPid);
            cluster.tools().barrier("client-barrier", 2).await();
            clientArrayFuture.get();

            long after = System.nanoTime();
            System.out.println("Done; elapsed=" + TimeUnit.NANOSECONDS.toMillis(after - before) + " ms");
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

    private static void installAsyncProfilerIfNeeded() throws IOException
    {
        File asyncProfilerHome = new File("target/async-profiler-2.0-linux-x64");
        if (!asyncProfilerHome.isDirectory())
        {
            System.out.println("installing async profiler...");
            File tarGzFile = new File("target/async-profiler-2.0-linux-x64.tar.gz");
            try (InputStream is = new URL("https://github.com/jvm-profiling-tools/async-profiler/releases/download/v2.0/async-profiler-2.0-linux-x64.tar.gz").openStream();
                 OutputStream os = new FileOutputStream(tarGzFile))
            {
                IOUtil.copy(is, os);
            }
            TarGZipUnArchiver ua = new TarGZipUnArchiver(tarGzFile);
            ua.enableLogging(new AbstractLogger(0, "") {
                @Override
                public void debug(String s, Throwable throwable)
                {

                }

                @Override
                public void info(String s, Throwable throwable)
                {

                }

                @Override
                public void warn(String s, Throwable throwable)
                {

                }

                @Override
                public void error(String s, Throwable throwable)
                {

                }

                @Override
                public void fatalError(String s, Throwable throwable)
                {

                }

                @Override
                public Logger getChildLogger(String s)
                {
                    return null;
                }
            });
            ua.setDestDirectory(tarGzFile.getParentFile());
            ua.extract();
        }
    }

    private static void spawnAsyncProfiler(String fgFilename, long pid) throws IOException
    {
        File asyncProfilerHome = new File("target/async-profiler-2.0-linux-x64");
        File fgFile = new File(fgFilename);
        new ProcessBuilder("./profiler.sh", "-d", "3600", "-f", fgFile.getAbsolutePath(), Long.toString(pid))
            .directory(asyncProfilerHome)
            .redirectErrorStream(true)
            .start();
    }
}
