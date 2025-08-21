package org.eclipse.jetty.perf.cometd;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.server.AbstractServerTransport;
import org.cometd.server.BayeuxServerImpl;
import org.cometd.server.JacksonJSONContextServer;
import org.cometd.server.ext.AcknowledgedMessagesExtension;
import org.cometd.server.http.JSONHttpTransport;
import org.cometd.server.http.TransportContext;
import org.cometd.server.http.jetty.CometDHandler;
import org.cometd.server.websocket.common.AbstractWebSocketEndPoint;
import org.cometd.server.websocket.common.AbstractWebSocketTransport;
import org.cometd.server.websocket.jetty.JettyWebSocketTransport;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;
import org.eclipse.jetty.perf.test.AbstractClusteredPerfTest;
import org.eclipse.jetty.perf.test.ClusteredTestContext;
import org.eclipse.jetty.perf.test.PerfTestParams;
import org.eclipse.jetty.perf.util.LatencyRecorder;
import org.eclipse.jetty.perf.util.Recorder;
import org.eclipse.jetty.perf.util.SerializableConsumer;
import org.eclipse.jetty.server.AbstractConnectionFactory;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.NanoTime;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Invocable;
import org.eclipse.jetty.util.thread.MonitoredQueuedThreadPool;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;
import org.mortbay.jetty.orchestrator.ClusterTools;
import org.mortbay.jetty.orchestrator.NodeArray;
import org.mortbay.jetty.orchestrator.NodeArrayFuture;
import org.mortbay.jetty.orchestrator.NodeJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.perf.util.ReportUtil.generateReport;

public class CometdClusteredPerfTest extends AbstractClusteredPerfTest
{
    private static final Logger LOG = LoggerFactory.getLogger(CometdClusteredPerfTest.class);

    private CometdClusteredPerfTest(String testName, Path reportRootPath, PerfTestParams perfTestParams, SerializableConsumer<PerfTestParams> perfTestParamsCustomizer) throws Exception
    {
        super(testName, reportRootPath, perfTestParams, perfTestParamsCustomizer);
    }

    public static void runTest(ClusteredTestContext clusteredTestContext, Invocable.InvocationType invocationType) throws Exception
    {
        runTest(clusteredTestContext, new PerfTestParams(), invocationType, p -> {});
    }

    public static void runTest(ClusteredTestContext clusteredTestContext, PerfTestParams perfTestParams, Invocable.InvocationType invocationType, SerializableConsumer<PerfTestParams> perfTestParamsCustomizer) throws Exception
    {
        try (CometdClusteredPerfTest clusteredPerfTest = new CometdClusteredPerfTest(clusteredTestContext.getTestName(), clusteredTestContext.getReportRootPath(), perfTestParams, perfTestParamsCustomizer))
        {
            clusteredPerfTest.execute(invocationType);
        }
    }

    private void execute(Invocable.InvocationType invocationType) throws Exception
    {
        LOG.info("Parameters:");
        perfTestParams.asMap().forEach((k, v) -> System.out.println("  " + k + " = '" + v + "'"));

        NodeArray serverArray = cluster.nodeArray("server");
        NodeArray loadersArray = cluster.nodeArray("loaders");

        NodeJob logSysInfo = tools -> LOG.info("{} '{}/{}': running JVM version '{}'",
            tools.getGlobalNodeId().getHostname(),
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            System.getProperty("java.vm.version"));
        List<NodeArrayFuture> futures = List.of(
            serverArray.executeOnAll(logSysInfo),
            loadersArray.executeOnAll(logSysInfo)
        );
        for (NodeArrayFuture future : futures)
        {
            future.get(30, TimeUnit.SECONDS);
        }

        LOG.info("Warming up...");
        serverArray.executeOnAll(tools ->
        {
            perfTestParamsCustomizer.accept(perfTestParams);
            startServer(perfTestParams, tools, invocationType);
        }).get(30, TimeUnit.SECONDS);
        loadersArray.executeOnAll(tools ->
        {
            perfTestParamsCustomizer.accept(perfTestParams);
            int batches = approximateBatches((int)perfTestParams.getWarmupDuration().toSeconds());
            LOG.info("Warmup batches: {}", batches);
            runClient(perfTestParams, tools, batches, false);
        }).get(10, TimeUnit.MINUTES);

        LOG.info("Running...");
        long before = System.nanoTime();

        serverArray.executeOnAll(tools ->
        {
            @SuppressWarnings("unchecked")
            List<Recorder> recorders = (List<Recorder>)tools.nodeEnvironment().get(Recorder.class.getName());
            recorders.forEach(Recorder::startRecording);
            ConfigurableMonitor configurableMonitor = new ConfigurableMonitor(perfTestParams.getMonitoredItems());
            tools.nodeEnvironment().put(ConfigurableMonitor.class.getName(), configurableMonitor);
        }).get(30, TimeUnit.SECONDS);
        loadersArray.executeOnAll(tools ->
        {
            try (ConfigurableMonitor ignore = new ConfigurableMonitor(perfTestParams.getMonitoredItems()))
            {
                perfTestParamsCustomizer.accept(perfTestParams);
                int batches = approximateBatches((int)perfTestParams.getRunDuration().toSeconds());
                LOG.info("Run batches: {}", batches);
                runClient(perfTestParams, tools, batches, true);
            }
        }).get(10, TimeUnit.MINUTES);
        serverArray.executeOnAll(tools ->
        {
            @SuppressWarnings("unchecked")
            List<Recorder> recorders = (List<Recorder>)tools.nodeEnvironment().get(Recorder.class.getName());
            recorders.forEach(Recorder::stopRecording);
            @SuppressWarnings("unchecked")
            List<LifeCycle> lifeCycles = (List<LifeCycle>)tools.nodeEnvironment().get(LifeCycle.class.getName());
            lifeCycles.forEach(l -> LifeCycle.stop(l));
            ConfigurableMonitor configurableMonitor = (ConfigurableMonitor)tools.nodeEnvironment().get(ConfigurableMonitor.class.getName());
            configurableMonitor.close();
        }).get(30, TimeUnit.SECONDS);
        loadersArray.executeOnAll(this::stopClient).get(30, TimeUnit.SECONDS);

        LOG.info("Generating report...");
        generateReport(Path.of(reportRootPath), perfTestParams.getNodeArrayIds(), cluster);

        long after = System.nanoTime();
        LOG.info("Done; elapsed={} ms", TimeUnit.NANOSECONDS.toMillis(after - before));
    }

    private static int approximateBatches(int seconds)
    {
        // a batch of 100 takes ~1.5s
        return (int)(Math.max(1, seconds / 1.5) * 100);
    }

    protected void startServer(PerfTestParams perfTestParams, ClusterTools clusterTools, Invocable.InvocationType invocationType) throws Exception
    {
        MonitoredQueuedThreadPool serverThreadPool = new MonitoredQueuedThreadPool(perfTestParams.SERVER_THREAD_POOL_SIZE);
        serverThreadPool.setReservedThreads(perfTestParams.SERVER_RESERVED_THREADS);
        Server server = new Server(serverThreadPool, null, null);
        QueuedThreadPool cometdThreadPool = new QueuedThreadPool();
        cometdThreadPool.setReservedThreads(0);
        BayeuxServerImpl bayeuxServer = new BayeuxServerImpl();
        bayeuxServer.setExecutor(cometdThreadPool);
        LatencyRecorder latencyRecorder = new LatencyRecorder("perf.hlog");
        MessageLatencyExtension messageLatencyExtension = new MessageLatencyExtension(latencyRecorder);
        boolean tls = perfTestParams.isTlsEnabled();

        SslContextFactory.Server sslContextFactory = null;
        if (tls) {
            Path keyStoreFile = Paths.get("src/main/resources/keystore.p12");
            if (!Files.exists(keyStoreFile)) {
                throw new FileNotFoundException(keyStoreFile.toString());
            }
            sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setKeyStorePath(keyStoreFile.toString());
            sslContextFactory.setKeyStoreType("pkcs12");
            sslContextFactory.setKeyStorePassword("storepwd");
        }

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        ConnectionFactory http = new HttpConnectionFactory(httpConfiguration);
        HTTP2ServerConnectionFactory http2 = tls ? new HTTP2ServerConnectionFactory(httpConfiguration) : new HTTP2CServerConnectionFactory(httpConfiguration);
        ConnectionFactory[] factories = {http, http2};
        if (tls) {
            ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
            alpn.setDefaultProtocol(http.getProtocol());
            factories = AbstractConnectionFactory.getFactories(sslContextFactory, alpn, http, http2);
        }
        ServerConnector connector = new ServerConnector(server, null, null, null, perfTestParams.SERVER_ACCEPTOR_COUNT, perfTestParams.SERVER_SELECTOR_COUNT, factories);
        connector.setPort(perfTestParams.getServerPort());
        server.addConnector(connector);

        JettyWebSocketTransport wsTransport = new JettyWebSocketTransport(bayeuxServer) {
            @Override
            protected void writeComplete(AbstractWebSocketEndPoint.Context context, List<ServerMessage> messages) {
                messageLatencyExtension.complete(messages);
            }
        };
        wsTransport.setOption(AbstractWebSocketTransport.ENABLE_EXTENSION_PREFIX_OPTION + "permessage-deflate", false);
        bayeuxServer.addTransport(wsTransport);
        bayeuxServer.addTransport(new JSONHttpTransport(bayeuxServer) {
            @Override
            protected void writeComplete(TransportContext context, List<ServerMessage> messages) {
                messageLatencyExtension.complete(messages);
            }
        });

        String cometdURLMapping = "/cometd/*";

        // Make sure the expiration timeout is large to avoid clients to timeout
        // This value must be several times larger than the client value
        // (e.g. 60 s on server vs 5 s on client) so that it's guaranteed that
        // it will be the client to dispose idle connections.
        bayeuxServer.setOption(AbstractServerTransport.MAX_INTERVAL_OPTION, String.valueOf(10 * 5000L));
        // Explicitly set the timeout value.
        bayeuxServer.setOption(AbstractServerTransport.TIMEOUT_OPTION, String.valueOf(20000L));
        // Use the faster JSON parser/generator.
        bayeuxServer.setOption(AbstractServerTransport.JSON_CONTEXT_OPTION, JacksonJSONContextServer.class.getName());
        bayeuxServer.setOption(AbstractWebSocketTransport.COMETD_URL_MAPPING_OPTION, cometdURLMapping);
        bayeuxServer.addExtension(new AcknowledgedMessagesExtension());
        bayeuxServer.addExtension(messageLatencyExtension);

        ContextHandler context = new ContextHandler("/cometd");
        server.setHandler(context);
        context.getContext().setAttribute(BayeuxServer.ATTRIBUTE, bayeuxServer);
        context.setAttribute(ContextHandler.MANAGED_ATTRIBUTES, BayeuxServer.ATTRIBUTE);

        WebSocketUpgradeHandler wsHandler = WebSocketUpgradeHandler.from(server, context, container ->
            container.setInvocationType(invocationType));
        context.setHandler(wsHandler);

        wsHandler.setHandler(new CometDHandler(invocationType));
        clusterTools.nodeEnvironment().put(LifeCycle.class.getName(), Arrays.asList(server, bayeuxServer));
        clusterTools.nodeEnvironment().put(Server.class.getName(), server);
        clusterTools.nodeEnvironment().put(Recorder.class.getName(), List.of(new Recorder()
        {
            @Override
            public void startRecording()
            {
                serverThreadPool.reset();
            }

            @Override
            public void stopRecording()
            {
                try
                {
                    try (PrintWriter printWriter = new PrintWriter("ServerMonitoredQueuedThreadPool.txt"))
                    {
                        printWriter.println(String.format("Average queue latency=%d", serverThreadPool.getAverageQueueLatency()));
                        printWriter.println(String.format("Max queue latency=%d", serverThreadPool.getMaxQueueLatency()));
                        printWriter.println(String.format("Max queue size=%d", serverThreadPool.getMaxQueueSize()));
                        printWriter.println(String.format("Average task latency=%d", serverThreadPool.getAverageTaskLatency()));
                        printWriter.println(String.format("Max task latency=%d", serverThreadPool.getMaxTaskLatency()));
                        printWriter.println(String.format("Max busy threads=%d", serverThreadPool.getMaxBusyThreads()));
                    }

                    try (PrintWriter printWriter = new PrintWriter("ServerDump.txt"))
                    {
                        server.dump(printWriter);
                    }
                }
                catch (Exception e)
                {
                    LOG.error("Error writing server reports", e);
                }
            }
        }, latencyRecorder));

        server.start();
        bayeuxServer.start();
    }

    protected void runClient(PerfTestParams perfTestParams, ClusterTools clusterTools, int batches, boolean recordHistogram) throws Exception
    {
        int clientId = clusterTools.barrier("cometd-client-id-barrier", perfTestParams.getLoadersCount()).await();
        CometDLoadClient client = new CometDLoadClient(recordHistogram);
        client.host = perfTestParams.getServerUri().getHost();
        client.port = perfTestParams.getServerPort();
        client.channel = "/a/" + clientId;
        client.batches = batches;
        if (perfTestParams.getHttpVersion().equals(HttpVersion.HTTP_2))
            client.http2 = true;
        client.run();
        clusterTools.nodeEnvironment().put(CometDLoadClient.class.getName(), client);
    }

    protected void stopClient(ClusterTools clusterTools)
    {
        CometDLoadClient client = (CometDLoadClient)clusterTools.nodeEnvironment().get(CometDLoadClient.class.getName());
        client.disconnect();
    }

    private static class MessageLatencyExtension implements BayeuxServer.Extension
    {
        private static final String SERVER_TIME_FIELD = "serverTime";

        private final LatencyRecorder latencyRecorder;

        private MessageLatencyExtension(LatencyRecorder latencyRecorder)
        {
            this.latencyRecorder = latencyRecorder;
        }

        @Override
        public boolean rcv(ServerSession session, ServerMessage.Mutable message) {
            if (message.getChannel().startsWith("/bench/")) {
                String id = (String)message.getDataAsMap().get("msg_id");
                if (id != null) {
                    message.put(SERVER_TIME_FIELD, NanoTime.now());
                }
            }
            return true;
        }

        private void complete(List<ServerMessage> messages) {
            for (ServerMessage message : messages) {
                if (message.getChannel().startsWith("/bench/")) {
                    String id = (String)message.getDataAsMap().get("msg_id");
                    if (id != null) {
                        Long serverTime = (Long)message.get(SERVER_TIME_FIELD);
                        if (serverTime != null) {
                            latencyRecorder.recordValue(NanoTime.since(serverTime));
                        }
                    }
                }
            }
        }
    }
}
