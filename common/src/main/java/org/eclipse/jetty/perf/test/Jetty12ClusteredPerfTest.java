package org.eclipse.jetty.perf.test;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.perf.histogram.loader.ResponseStatusListener;
import org.eclipse.jetty.perf.histogram.loader.ResponseTimeListener;
import org.eclipse.jetty.perf.httpclient.StatisticalSyncSocketAddressResolver;
import org.eclipse.jetty.perf.util.IOUtil;
import org.eclipse.jetty.perf.util.LatencyRecorder;
import org.eclipse.jetty.perf.util.PlatformMonitorRecorder;
import org.eclipse.jetty.perf.util.Recorder;
import org.eclipse.jetty.perf.util.SerializableConsumer;
import org.eclipse.jetty.perf.util.SerializableSupplier;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.HttpStream;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.VirtualThreads;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.MonitoredQueuedThreadPool;
import org.mortbay.jetty.load.generator.HTTP1ClientTransportBuilder;
import org.mortbay.jetty.load.generator.HTTP2ClientTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.orchestrator.ClusterTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Jetty12ClusteredPerfTest extends AbstractClusteredPerfTest
{
    private static final Logger LOG = LoggerFactory.getLogger(Jetty12ClusteredPerfTest.class);

    private final SerializableSupplier<Handler> testedHandlerSupplier;

    private Jetty12ClusteredPerfTest(String testName, Path reportRootPath, PerfTestParams perfTestParams, SerializableSupplier<Handler> testedHandlerSupplier, SerializableConsumer<PerfTestParams> perfTestParamsCustomizer) throws Exception
    {
        super(testName, reportRootPath, perfTestParams, perfTestParamsCustomizer);
        this.testedHandlerSupplier = testedHandlerSupplier;
    }

    public static void runTest(ClusteredTestContext clusteredTestContext, SerializableSupplier<Handler> testedHandlerSupplier) throws Exception
    {
        runTest(clusteredTestContext, new PerfTestParams(), testedHandlerSupplier, p ->
        {});
    }

    public static void runTest(ClusteredTestContext clusteredTestContext, SerializableSupplier<Handler> testedHandlerSupplier, SerializableConsumer<PerfTestParams> perfTestParamsCustomizer) throws Exception
    {
        runTest(clusteredTestContext, new PerfTestParams(), testedHandlerSupplier, perfTestParamsCustomizer);
    }

    public static void runTest(ClusteredTestContext clusteredTestContext, PerfTestParams perfTestParams, SerializableSupplier<Handler> testedHandlerSupplier, SerializableConsumer<PerfTestParams> perfTestParamsCustomizer) throws Exception
    {
        try (Jetty12ClusteredPerfTest clusteredPerfTest = new Jetty12ClusteredPerfTest(clusteredTestContext.getTestName(), clusteredTestContext.getReportRootPath(), perfTestParams, testedHandlerSupplier, perfTestParamsCustomizer))
        {
            clusteredPerfTest.execute();
        }
    }

    protected void startServer(PerfTestParams perfTestParams, ClusterTools clusterTools) throws Exception
    {
        MonitoredQueuedThreadPool qtp = new MonitoredQueuedThreadPool(perfTestParams.SERVER_THREAD_POOL_SIZE);
        qtp.setReservedThreads(perfTestParams.SERVER_RESERVED_THREADS);

        if (perfTestParams.SERVER_USE_VIRTUAL_THREADS)
        {
            Executor defaultVirtualThreadsExecutor = VirtualThreads.getDefaultVirtualThreadsExecutor();
            if (defaultVirtualThreadsExecutor == null)
                LOG.warn("Virtual threads are not supported by this JVM, ignoring that parameter");
            qtp.setVirtualThreadsExecutor(defaultVirtualThreadsExecutor);
        }

        ByteBufferPool bufferPool;
        if (perfTestParams.SERVER_USE_BYTE_BUFFER_POOLING)
            bufferPool = new ArrayByteBufferPool();
        else
            bufferPool = new ByteBufferPool.NonPooling();

        Server server = new Server(qtp, null, bufferPool);

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        if (perfTestParams.isTlsEnabled())
        {
            SecureRequestCustomizer customizer = new SecureRequestCustomizer();
            customizer.setSniHostCheck(false);
            httpConfiguration.addCustomizer(customizer);
        }

        ConnectionFactory http;
        if (perfTestParams.getHttpVersion() == HttpVersion.HTTP_2)
            http = new HTTP2CServerConnectionFactory(httpConfiguration);
        else
            http = new HttpConnectionFactory(httpConfiguration);

        List<ConnectionFactory> connectionFactories = new ArrayList<>();
        if (perfTestParams.isTlsEnabled())
        {
            SslContextFactory.Server serverSslContextFactory = new SslContextFactory.Server();
            // Copy keystore from classpath to temp file.
            URL resource = Objects.requireNonNull(getClass().getResource("/keystore.p12"));
            Path targetTmpFolder = Paths.get(System.getProperty("java.io.tmpdir")).resolve(getClass().getSimpleName());
            Files.createDirectories(targetTmpFolder);
            Path targetKeystore = targetTmpFolder.resolve("keystore.p12");
            try (InputStream inputStream = resource.openStream();
                 OutputStream outputStream = Files.newOutputStream(targetKeystore))
            {
                IOUtil.copy(inputStream, outputStream);
            }
            serverSslContextFactory.setKeyStorePath(targetKeystore.toString());
            serverSslContextFactory.setKeyStorePassword("storepwd");
            SslConnectionFactory ssl = new SslConnectionFactory(serverSslContextFactory, http.getProtocol());
            connectionFactories.add(ssl);
        }
        connectionFactories.add(http);

        ServerConnector serverConnector = new ServerConnector(server, perfTestParams.getServerAcceptorCount(), perfTestParams.getServerSelectorCount(), connectionFactories.toArray(new ConnectionFactory[0]));
        serverConnector.setPort(perfTestParams.getServerPort());

        server.addConnector(serverConnector);

        LatencyRecorder latencyRecorder = new LatencyRecorder("perf.hlog");
        Handler latencyRecordingHandler = new ModernLatencyRecordingHandler(testedHandlerSupplier.get(), latencyRecorder);
        StatisticsHandler statisticsHandler = new StatisticsHandler(latencyRecordingHandler);
        server.setHandler(statisticsHandler);
        server.start();

        Map<String, Object> env = clusterTools.nodeEnvironment();
        env.put(MonitoredQueuedThreadPool.class.getName(), qtp);
        env.put(StatisticsHandler.class.getName(), statisticsHandler);
        env.put(Recorder.class.getName(), List.of(new Recorder()
        {
            @Override
            public void startRecording()
            {
                statisticsHandler.reset();
                qtp.reset();
            }

            @Override
            public void stopRecording()
            {
                try
                {
                    try (PrintWriter printWriter = new PrintWriter("StatisticsHandler.txt"))
                    {
                        statisticsHandler.dump(printWriter);
                    }

                    try (PrintWriter printWriter = new PrintWriter("MonitoredQueuedThreadPool.txt"))
                    {
                        printWriter.println(String.format("Average queue latency=%d", qtp.getAverageQueueLatency()));
                        printWriter.println(String.format("Max queue latency=%d", qtp.getMaxQueueLatency()));
                        printWriter.println(String.format("Max queue size=%d", qtp.getMaxQueueSize()));
                        printWriter.println(String.format("Average task latency=%d", qtp.getAverageTaskLatency()));
                        printWriter.println(String.format("Max task latency=%d", qtp.getMaxTaskLatency()));
                        printWriter.println(String.format("Max busy threads=%d", qtp.getMaxBusyThreads()));
                    }

                    try (PrintWriter printWriter = new PrintWriter("ServerDump.txt"))
                    {
                        server.dump(printWriter);
                    }

                    // The server dump has been taken with the clients still connected, they can now be freed to complete their shutdown.
                    clusterTools.barrier("clients-end-barrier", perfTestParams.getParticipantCount() - 1).await(30, TimeUnit.SECONDS);
                }
                catch (Exception e)
                {
                    LOG.error("Error writing server reports", e);
                }
            }
        }, new PlatformMonitorRecorder(), latencyRecorder));
        env.put(CompletableFuture.class.getName(), CompletableFuture.completedFuture(null));
        env.put(Server.class.getName(), server);
    }

    protected void stopServer(PerfTestParams perfTestParams, ClusterTools clusterTools) throws Exception
    {
        ConcurrentMap<String, Object> env = clusterTools.nodeEnvironment();
        Server server = (Server)env.get(Server.class.getName());
        server.stop();
    }

    protected void runLoadGenerator(PerfTestParams perfTestParams, ClusterTools clusterTools) throws Exception
    {
        LatencyRecorder latencyRecorder = new LatencyRecorder("perf.hlog");
        ResponseTimeListener responseTimeListener = new ResponseTimeListener(latencyRecorder);
        ResponseStatusListener responseStatusListener = new ResponseStatusListener("http-client-statuses.log");
        Map<String, Object> env = clusterTools.nodeEnvironment();
        env.put(Recorder.class.getName(), List.of(new PlatformMonitorRecorder(), latencyRecorder, responseStatusListener));

        URI serverUri = perfTestParams.getServerUri();
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .socketAddressResolver(new StatisticalSyncSocketAddressResolver())
            .scheme(serverUri.getScheme())
            .host(serverUri.getHost())
            .port(serverUri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(perfTestParams.getWarmupDuration().plus(perfTestParams.getRunDuration()).toSeconds(), TimeUnit.SECONDS)
            .threads(perfTestParams.getLoaderThreads())
            .rateRampUpPeriod(perfTestParams.getWarmupDuration().toSeconds() / 2)
            .resourceRate(perfTestParams.getLoaderRate())
            .resource(new Resource(serverUri.getPath()))
            .resourceListener(responseTimeListener)
            .listener(responseTimeListener)
            .resourceListener(responseStatusListener)
            .listener(responseStatusListener)
            .listener((LoadGenerator.CompleteListener)generator ->
            {
                try
                {
                    try (PrintWriter printWriter = new PrintWriter("LoadGeneratorDump.txt"))
                    {
                        generator.dump(printWriter);
                    }
                    // Keep the clients connected to the server until the latter has dumped its state.
                    clusterTools.barrier("clients-end-barrier", perfTestParams.getParticipantCount() - 1).await(30, TimeUnit.SECONDS);
                }
                catch (Exception e)
                {
                    LOG.error("Error stopping LoadGenerator", e);
                }
            });

        if (perfTestParams.getHttpVersion().getVersion() <= 11)
        {
            builder.httpClientTransportBuilder(new HTTP1ClientTransportBuilder()
            {
                @Override
                protected HttpClientTransport newHttpClientTransport(ClientConnector connector)
                {
                    HttpClientTransport transport = super.newHttpClientTransport(connector);
                    transport.setConnectionPoolFactory(perfTestParams.buildLoaderConnectionPoolFactory());
                    return transport;
                }
            });
        }
        else if (perfTestParams.getHttpVersion() == HttpVersion.HTTP_2)
        {
            builder.httpClientTransportBuilder(new HTTP2ClientTransportBuilder()
            {
                @Override
                protected HttpClientTransport newHttpClientTransport(ClientConnector connector)
                {
                    HttpClientTransport transport = super.newHttpClientTransport(connector);
                    transport.setConnectionPoolFactory(perfTestParams.buildLoaderConnectionPoolFactory());
                    return transport;
                }
            });
        }

        LoadGenerator loadGenerator = builder.build();
        LOG.info("load generation begin with client '{}'", HttpClient.USER_AGENT);
        CompletableFuture<Void> cf = loadGenerator.begin();
        cf = cf.whenComplete((x, f) ->
        {
            if (f == null)
            {
                LOG.info("load generation complete");
            }
            else
            {
                LOG.info("load generation failure", f);
            }
        });
        env.put(CompletableFuture.class.getName(), cf);
    }

    protected void runProbeGenerator(PerfTestParams perfTestParams, ClusterTools clusterTools) throws Exception
    {
        LatencyRecorder latencyRecorder = new LatencyRecorder("perf.hlog");
        ResponseTimeListener responseTimeListener = new ResponseTimeListener(latencyRecorder);
        ResponseStatusListener responseStatusListener = new ResponseStatusListener("http-client-statuses.log");
        Map<String, Object> env = clusterTools.nodeEnvironment();
        env.put(Recorder.class.getName(), List.of(new PlatformMonitorRecorder(), latencyRecorder, responseStatusListener));

        URI serverUri = perfTestParams.getServerUri();
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(serverUri.getScheme())
            .host(serverUri.getHost())
            .port(serverUri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(perfTestParams.getWarmupDuration().plus(perfTestParams.getRunDuration()).toSeconds(), TimeUnit.SECONDS)
            .threads(1)
            .resourceRate(perfTestParams.getProbeRate())
            .resource(new Resource(serverUri.getPath()))
            .resourceListener(responseTimeListener)
            .listener(responseTimeListener)
            .resourceListener(responseStatusListener)
            .listener(responseStatusListener)
            .listener((LoadGenerator.CompleteListener)generator ->
            {
                try
                {
                    try (PrintWriter printWriter = new PrintWriter("LoadGeneratorDump.txt"))
                    {
                        generator.dump(printWriter);
                    }

                    // Keep the clients connected to the server until the latter has dumped its state.
                    clusterTools.barrier("clients-end-barrier", perfTestParams.getParticipantCount() - 1).await(30, TimeUnit.SECONDS);
                }
                catch (Exception e)
                {
                    LOG.error("Error stopping LoadGenerator", e);
                }
            });

        if (perfTestParams.getHttpVersion().getVersion() <= 11)
        {
            builder.httpClientTransportBuilder(new HTTP1ClientTransportBuilder());
        }
        else if (perfTestParams.getHttpVersion() == HttpVersion.HTTP_2)
        {
            builder.httpClientTransportBuilder(new HTTP2ClientTransportBuilder());
        }

        LoadGenerator loadGenerator = builder.build();
        LOG.info("probe generation begin with client '{}'", HttpClient.USER_AGENT);
        CompletableFuture<Void> cf = loadGenerator.begin();
        cf = cf.whenComplete((x, f) ->
        {
            if (f == null)
            {
                LOG.info("probe generation complete");
            }
            else
            {
                LOG.info("probe generation failure", f);
            }
        });
        env.put(CompletableFuture.class.getName(), cf);
    }

    /**
     * Identical to what 11.0.x does
     */
    static class ModernLatencyRecordingHandler extends Handler.Wrapper
    {
        private final LatencyRecorder recorder;

        public ModernLatencyRecordingHandler(Handler handler, LatencyRecorder recorder)
        {
            super(handler);
            this.recorder = recorder;
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception
        {
            request.addHttpStreamWrapper(httpStream -> new HttpStream.Wrapper(httpStream)
            {
                @Override
                public void succeeded()
                {
                    super.succeeded();
                    recorder.recordValue(System.nanoTime() - request.getBeginNanoTime());
                }

                @Override
                public void failed(Throwable x)
                {
                    super.failed(x);
                    recorder.recordValue(System.nanoTime() - request.getBeginNanoTime());
                }
            });
            return super.handle(request, response, callback);
        }
    }

    /**
     * Comparable to what 11.0.x does
     */
    static class LegacyLatencyRecordingHandler extends Handler.Wrapper
    {
        private final LatencyRecorder recorder;

        public LegacyLatencyRecordingHandler(Handler handler, LatencyRecorder recorder)
        {
            super(handler);
            this.recorder = recorder;
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception
        {
            request.addHttpStreamWrapper(httpStream -> new HttpStream.Wrapper(httpStream)
            {
                final long before = System.nanoTime();

                @Override
                public void succeeded()
                {
                    super.succeeded();
                    recorder.recordValue(System.nanoTime() - before);
                }

                @Override
                public void failed(Throwable x)
                {
                    super.failed(x);
                    recorder.recordValue(System.nanoTime() - before);
                }
            });
            return super.handle(request, response, callback);
        }
    }
}
