package org.eclipse.jetty.perf.test;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.perf.handler.LegacyLatencyRecordingHandler;
import org.eclipse.jetty.perf.histogram.loader.ResponseStatusListener;
import org.eclipse.jetty.perf.histogram.loader.ResponseTimeListener;
import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;
import org.eclipse.jetty.perf.util.IOUtil;
import org.eclipse.jetty.perf.util.LatencyRecorder;
import org.eclipse.jetty.perf.util.SerializableSupplier;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.mortbay.jetty.load.generator.HTTP2ClientTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.NodeArray;
import org.mortbay.jetty.orchestrator.NodeArrayFuture;
import org.mortbay.jetty.orchestrator.NodeJob;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.perf.util.ReportUtil.generateReport;

public class ClusteredPerfTest implements Serializable, Closeable
{
    private static final Logger LOG = LoggerFactory.getLogger(ClusteredPerfTest.class);

    private final Duration warmupDuration;
    private final Duration runDuration;
    private final EnumSet<ConfigurableMonitor.Item> monitoredItems;
    private final PerfTestParams.Protocol protocol;
    private final URI serverUri;
    private final int loaderRate;
    private final int probeRate;
    private final String reportRootPath; // java.nio.Path isn't serializable, so we must use a String.
    private final SerializableSupplier<Handler> testedHandlerSupplier;
    private final int participantCount;
    private final Collection<String> nodeArrayIds;
    private transient Cluster cluster; // not serializable, but there is no need to access this field from remote lambdas.

    public ClusteredPerfTest(String testName, PerfTestParams perfTestParams, Duration warmupDuration, Duration runDuration, SerializableSupplier<Handler> testedHandlerSupplier, Path reportRootPath) throws Exception
    {
        this.warmupDuration = warmupDuration;
        this.runDuration = runDuration;
        this.monitoredItems = perfTestParams.getMonitoredItems();
        this.protocol = perfTestParams.getProtocol();
        this.serverUri = perfTestParams.getServerUri();
        this.loaderRate = perfTestParams.getLoaderRate();
        this.probeRate = perfTestParams.getProbeRate();
        this.testedHandlerSupplier = testedHandlerSupplier;
        this.reportRootPath = reportRootPath.toString();
        ClusterConfiguration clusterConfiguration = perfTestParams.getClusterConfiguration();
        this.participantCount = clusterConfiguration.nodeArrays().stream().mapToInt(na -> na.nodes().size()).sum() + 1; // + 1 b/c of the test itself
        this.nodeArrayIds = clusterConfiguration.nodeArrays().stream().map(NodeArrayConfiguration::id).toList();
        this.cluster = new Cluster(testName, clusterConfiguration);
    }

    @Override
    public void close()
    {
        if (cluster != null)
        {
            cluster.close();
            cluster = null;
        }
    }

    public void execute() throws Exception
    {
        NodeArray serverArray = cluster.nodeArray("server");
        NodeArray loadersArray = cluster.nodeArray("loaders");
        NodeArray probeArray = cluster.nodeArray("probe");

        NodeJob logSystemProps = tools -> LOG.info("JVM version '{}' running on '{}/{}'", System.getProperty("java.vm.version"), System.getProperty("os.name"), System.getProperty("os.arch"));
        serverArray.executeOnAll(logSystemProps).get();
        loadersArray.executeOnAll(logSystemProps).get();
        probeArray.executeOnAll(logSystemProps).get();

        // start the server and the generators
        serverArray.executeOnAll(tools -> startServer(protocol, serverUri.getPort(), tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);
        loadersArray.executeOnAll(tools -> runLoadGenerator(protocol, serverUri, loaderRate, warmupDuration, runDuration, tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);
        probeArray.executeOnAll(tools -> runProbeGenerator(protocol, serverUri, probeRate, warmupDuration, runDuration, tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);

        LOG.info("Warming up...");
        Thread.sleep(warmupDuration.toMillis());

        LOG.info("Running...");
        long before = System.nanoTime();

        NodeArrayFuture serverFuture = serverArray.executeOnAll(tools ->
        {
            try (ConfigurableMonitor ignore = new ConfigurableMonitor(monitoredItems))
            {
                LatencyRecorder latencyRecorder = (LatencyRecorder)tools.nodeEnvironment().get(LatencyRecorder.class.getName());
                latencyRecorder.startRecording();
                tools.barrier("run-start-barrier", participantCount).await();
                tools.barrier("run-end-barrier", participantCount).await();
                latencyRecorder.stopRecording();
            }
        });

        NodeArrayFuture loadersFuture = loadersArray.executeOnAll(tools ->
        {
            try (ConfigurableMonitor ignore = new ConfigurableMonitor(monitoredItems))
            {
                LatencyRecorder latencyRecorder = (LatencyRecorder)tools.nodeEnvironment().get(LatencyRecorder.class.getName());
                latencyRecorder.startRecording();
                ResponseStatusListener responseStatusListener = (ResponseStatusListener)tools.nodeEnvironment().get(ResponseStatusListener.class.getName());
                responseStatusListener.startRecording();
                tools.barrier("run-start-barrier", participantCount).await();
                tools.barrier("run-end-barrier", participantCount).await();
                latencyRecorder.stopRecording();
                responseStatusListener.stopRecording();
                CompletableFuture<?> cf = (CompletableFuture<?>)tools.nodeEnvironment().get(CompletableFuture.class.getName());
                cf.get();
            }
        });

        NodeArrayFuture probeFuture = probeArray.executeOnAll(tools ->
        {
            try (ConfigurableMonitor ignore = new ConfigurableMonitor(monitoredItems))
            {
                LatencyRecorder latencyRecorder = (LatencyRecorder)tools.nodeEnvironment().get(LatencyRecorder.class.getName());
                latencyRecorder.startRecording();
                ResponseStatusListener responseStatusListener = (ResponseStatusListener)tools.nodeEnvironment().get(ResponseStatusListener.class.getName());
                responseStatusListener.startRecording();
                tools.barrier("run-start-barrier", participantCount).await();
                tools.barrier("run-end-barrier", participantCount).await();
                latencyRecorder.stopRecording();
                responseStatusListener.stopRecording();
                CompletableFuture<?> cf = (CompletableFuture<?>)tools.nodeEnvironment().get(CompletableFuture.class.getName());
                cf.get();
            }
        });

        try
        {
            try
            {
                // signal all participants to start
                cluster.tools().barrier("run-start-barrier", participantCount).await(30, TimeUnit.SECONDS);
                // wait for the run duration
                Thread.sleep(runDuration.toMillis());
                // signal all participants to stop
                cluster.tools().barrier("run-end-barrier", participantCount).await(30, TimeUnit.SECONDS);
            }
            finally
            {
                // wait for all report files to be written;
                // do it in a finally so that if the above barrier awaits time out b/c a job threw an exception
                // the future.get() call will re-throw the exception and it'll be logged.
                serverFuture.get(30, TimeUnit.SECONDS);
                loadersFuture.get(30, TimeUnit.SECONDS);
                probeFuture.get(30, TimeUnit.SECONDS);
            }

            // stop server
            serverArray.executeOnAll((tools) -> stopServer(tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);

            LOG.info("Generating report...");
            generateReport(Path.of(reportRootPath), nodeArrayIds, cluster);

            long after = System.nanoTime();
            LOG.info("Done; elapsed={} ms", TimeUnit.NANOSECONDS.toMillis(after - before));
        }
        catch (Exception e)
        {
            NodeJob dump = (tools) ->
            {
                ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                System.err.println("---- ---- ---- ---- ---- ---- ---- ----");
                for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(true, true))
                {
                    System.err.println(threadInfo);
                }
                System.err.println("---- ---- ---- ---- ---- ---- ---- ----");
            };
            serverArray.executeOnAll(dump).get();
            loadersArray.executeOnAll(dump).get();
            probeArray.executeOnAll(dump).get();

            throw e;
        }
    }


    private void startServer(PerfTestParams.Protocol protocol, int serverPort, Map<String, Object> env) throws Exception
    {
        Server server = new Server();

//        MBeanContainer mbContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
//        server.addBean(mbContainer);

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        if (protocol.isSecure())
        {
            SecureRequestCustomizer customizer = new SecureRequestCustomizer();
            customizer.setSniHostCheck(false);
            httpConfiguration.addCustomizer(customizer);
        }

        ConnectionFactory http;
        if (protocol.getVersion() == PerfTestParams.HttpVersion.HTTP2)
            http = new HTTP2CServerConnectionFactory(httpConfiguration);
        else
            http = new HttpConnectionFactory(httpConfiguration);

        List<ConnectionFactory> connectionFactories = new ArrayList<>();
        if (protocol.isSecure())
        {
            SslContextFactory.Server serverSslContextFactory = new SslContextFactory.Server();
            // Copy keystore from classpath to temp file.
            URL resource = Objects.requireNonNull(getClass().getResource("/keystore.p12"));
            Path targetTmpFolder = Paths.get(System.getProperty("java.io.tmpdir")).resolve(getClass().getSimpleName());
            Files.createDirectories(targetTmpFolder);
            Path targetKeystore = targetTmpFolder.resolve("keystore.p12");
            try (InputStream inputStream = resource.openStream(); OutputStream outputStream = Files.newOutputStream(targetKeystore))
            {
                IOUtil.copy(inputStream, outputStream);
            }
            serverSslContextFactory.setKeyStorePath(targetKeystore.toString());
            serverSslContextFactory.setKeyStorePassword("storepwd");
            SslConnectionFactory ssl = new SslConnectionFactory(serverSslContextFactory, http.getProtocol());
            connectionFactories.add(ssl);
        }
        connectionFactories.add(http);

        ServerConnector serverConnector = new ServerConnector(server, 4, 24, connectionFactories.toArray(new ConnectionFactory[0]));
        serverConnector.setPort(serverPort);

        // register LatencyRecorder on the server to get it lifecycled such as the recoding is stopped with the server
        LatencyRecorder latencyRecorder = new LatencyRecorder("perf.hlog");
        server.addBean(latencyRecorder);

        server.addConnector(serverConnector);

        Handler.Wrapper latencyRecordingHandler = new LegacyLatencyRecordingHandler(latencyRecorder);
        latencyRecordingHandler.setHandler(testedHandlerSupplier);
        server.setHandler(latencyRecordingHandler);
        server.start();

        env.put(LatencyRecorder.class.getName(), latencyRecorder);
        env.put(Server.class.getName(), server);
    }


    private void stopServer(Map<String, Object> env) throws Exception
    {
        ((Server)env.get(Server.class.getName())).stop();
    }

    private void runLoadGenerator(PerfTestParams.Protocol protocol, URI serverUri, int loaderRate, Duration warmupDuration, Duration runDuration, Map<String, Object> env) throws Exception
    {
        LatencyRecorder latencyRecorder = new LatencyRecorder("perf.hlog");
        ResponseTimeListener responseTimeListener = new ResponseTimeListener(latencyRecorder);
        env.put(LatencyRecorder.class.getName(), latencyRecorder);
        ResponseStatusListener responseStatusListener = new ResponseStatusListener("http-client-statuses.log");
        env.put(ResponseStatusListener.class.getName(), responseStatusListener);

        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(serverUri.getScheme())
            .host(serverUri.getHost())
            .port(serverUri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(warmupDuration.plus(runDuration).toSeconds(), TimeUnit.SECONDS)
            .threads(2)
            .rateRampUpPeriod(warmupDuration.toSeconds() / 2)
            .resourceRate(loaderRate)
            .resource(new Resource(serverUri.getPath()))
            .resourceListener(responseTimeListener)
            .listener(responseTimeListener)
            .resourceListener(responseStatusListener)
            .listener(responseStatusListener)
            ;

        if (protocol.getVersion() == PerfTestParams.HttpVersion.HTTP2)
        {
            builder.httpClientTransportBuilder(new HTTP2ClientTransportBuilder());
        }

        LoadGenerator loadGenerator = builder.build();
        LOG.info("load generation begin");
        CompletableFuture<Void> cf = loadGenerator.begin();
        cf = cf.whenComplete((x, f) -> {
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

    private void runProbeGenerator(PerfTestParams.Protocol protocol, URI serverUri, int probeRate, Duration warmupDuration, Duration runDuration, Map<String, Object> env) throws Exception
    {
        LatencyRecorder latencyRecorder = new LatencyRecorder("perf.hlog");
        ResponseTimeListener responseTimeListener = new ResponseTimeListener(latencyRecorder);
        env.put(LatencyRecorder.class.getName(), latencyRecorder);
        ResponseStatusListener responseStatusListener = new ResponseStatusListener("http-client-statuses.log");
        env.put(ResponseStatusListener.class.getName(), responseStatusListener);

        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(serverUri.getScheme())
            .host(serverUri.getHost())
            .port(serverUri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(warmupDuration.plus(runDuration).toSeconds(), TimeUnit.SECONDS)
            .threads(1)
            .rateRampUpPeriod(warmupDuration.toSeconds() / 2)
            .resourceRate(probeRate)
            .resource(new Resource(serverUri.getPath()))
            .resourceListener(responseTimeListener)
            .listener(responseTimeListener)
            .resourceListener(responseStatusListener)
            .listener(responseStatusListener)
            ;

        if (protocol.getVersion() == PerfTestParams.HttpVersion.HTTP2)
        {
            builder.httpClientTransportBuilder(new HTTP2ClientTransportBuilder());
        }

        LoadGenerator loadGenerator = builder.build();
        LOG.info("probe generation begin");
        CompletableFuture<Void> cf = loadGenerator.begin();
        cf = cf.whenComplete((x, f) -> {
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
}
