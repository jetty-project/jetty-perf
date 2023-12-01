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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.perf.handler.ModernLatencyRecordingHandler;
import org.eclipse.jetty.perf.histogram.loader.ResponseStatusListener;
import org.eclipse.jetty.perf.histogram.loader.ResponseTimeListener;
import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;
import org.eclipse.jetty.perf.util.IOUtil;
import org.eclipse.jetty.perf.util.LatencyRecorder;
import org.eclipse.jetty.perf.util.Recorder;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.perf.util.ReportUtil.generateReport;

public class ClusteredPerfTest implements Serializable, Closeable
{
    private static final Logger LOG = LoggerFactory.getLogger(ClusteredPerfTest.class);

    private final PerfTestParams perfTestParams;
    private final String reportRootPath; // java.nio.Path isn't serializable, so we must use a String.
    private final SerializableSupplier<Handler> testedHandlerSupplier;
    private transient Cluster cluster; // not serializable, but there is no need to access this field from remote lambdas.

    public ClusteredPerfTest(String testName, PerfTestParams perfTestParams, SerializableSupplier<Handler> testedHandlerSupplier, Path reportRootPath) throws Exception
    {
        this.perfTestParams = perfTestParams;
        this.testedHandlerSupplier = testedHandlerSupplier;
        this.reportRootPath = reportRootPath.toString();
        this.cluster = perfTestParams.buildCluster(testName);
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

        NodeJob logSysInfo = tools -> LOG.info("{} '{}/{}': running JVM version '{}'",
            java.net.InetAddress.getLocalHost().getHostName(),
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            System.getProperty("java.vm.version"));
        List<NodeArrayFuture> futures = List.of(
            serverArray.executeOnAll(logSysInfo),
            loadersArray.executeOnAll(logSysInfo),
            probeArray.executeOnAll(logSysInfo)
        );
        for (NodeArrayFuture future : futures)
        {
            future.get(30, TimeUnit.SECONDS);
        }

        LOG.info("Starting the server...");
        serverArray.executeOnAll(tools -> startServer(perfTestParams, tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);
        LOG.info("Starting the loaders...");
        loadersArray.executeOnAll(tools -> runLoadGenerator(perfTestParams, tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);
        LOG.info("Starting the probe...");
        probeArray.executeOnAll(tools -> runProbeGenerator(perfTestParams, tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);

        LOG.info("Warming up {}s ...", perfTestParams.getWarmupDuration().toSeconds());
        Thread.sleep(perfTestParams.getWarmupDuration().toMillis());

        LOG.info("Running {}s ...", perfTestParams.getRunDuration().toSeconds());
        long before = System.nanoTime();

        NodeJob recordingJob = tools ->
        {
            try (ConfigurableMonitor ignore = new ConfigurableMonitor(perfTestParams.getMonitoredItems()))
            {
                @SuppressWarnings("unchecked")
                List<Recorder> recorders = (List<Recorder>)tools.nodeEnvironment().get(Recorder.class.getName());

                recorders.forEach(Recorder::startRecording);
                tools.barrier("run-start-barrier", perfTestParams.getParticipantCount()).await();
                tools.barrier("run-end-barrier", perfTestParams.getParticipantCount()).await();
                recorders.forEach(Recorder::stopRecording);

                CompletableFuture<?> cf = (CompletableFuture<?>)tools.nodeEnvironment().get(CompletableFuture.class.getName());
                cf.get();
            }
            catch (Throwable x)
            {
                LOG.error("Caught exception in job", x);
                throw x;
            }
        };
        NodeArrayFuture serverFuture = serverArray.executeOnAll(recordingJob);
        NodeArrayFuture loadersFuture = loadersArray.executeOnAll(recordingJob);
        NodeArrayFuture probeFuture = probeArray.executeOnAll(recordingJob);

        try
        {
            try
            {
                LOG.info("  Signalling all participants to start recording...");
                cluster.tools().barrier("run-start-barrier", perfTestParams.getParticipantCount()).await(30, TimeUnit.SECONDS);
                LOG.info("  Waiting for the duration of the run...");
                Thread.sleep(perfTestParams.getRunDuration().toMillis());
                LOG.info("  Signalling all participants to stop recording...");
                cluster.tools().barrier("run-end-barrier", perfTestParams.getParticipantCount()).await(30, TimeUnit.SECONDS);
                LOG.info("  Signalled all participants to stop recording");
            }
            finally
            {
                waitForFutures(30, TimeUnit.SECONDS, serverFuture, loadersFuture, probeFuture);
            }

            LOG.info("Stopping the server...");
            serverArray.executeOnAll((tools) -> stopServer(tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);

            LOG.info("Generating report...");
            generateReport(Path.of(reportRootPath), perfTestParams.getNodeArrayIds(), cluster);

            long after = System.nanoTime();
            LOG.info("Done; elapsed={} ms", TimeUnit.NANOSECONDS.toMillis(after - before));
        }
        catch (Exception e)
        {
            StringBuilder msg = new StringBuilder("Error stopping jobs");
            try
            {
                LOG.info("Downloading artefacts before rethrowing...");
                generateReport(Path.of(reportRootPath), perfTestParams.getNodeArrayIds(), cluster);

                LOG.info("Dumping threads of pending jobs before rethrowing...");
                NodeJob dump = (tools) ->
                {
                    String nodeId = tools.getNodeId();
                    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                    System.err.println("----- " + nodeId + " -----");
                    for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(true, true))
                    {
                        System.err.println(threadInfo);
                    }
                    System.err.println("----- " + nodeId + " -----");
                };
                serverArray.executeOn(serverFuture.getNotDoneNodeIds(), dump).get();
                for (String id : loadersFuture.getNotDoneNodeIds())
                {
                    loadersArray.executeOn(id, dump).get();
                }
                probeArray.executeOn(probeFuture.getNotDoneNodeIds(), dump).get();

                msg.append(String.format("; nodes that failed to stop: server=%s loaders=%s probe=%s",
                    serverFuture.getNotDoneNodeIds(), loadersFuture.getNotDoneNodeIds(), probeFuture.getNotDoneNodeIds()));
                LOG.error(msg.toString(), e);
            }
            catch (Exception subEx)
            {
                e.addSuppressed(subEx);
            }
            throw new Exception(msg.toString(), e);
        }
    }

    private void waitForFutures(long time, TimeUnit unit, NodeArrayFuture... futures) throws Exception
    {
        LOG.info("  Waiting for all report files to be written...");
        Exception ex = null;
        for (NodeArrayFuture future : futures)
        {
            try
            {
                future.get(time, unit);
            }
            catch (Exception e)
            {
                if (ex == null)
                    ex = e;
                else
                    ex.addSuppressed(e);
            }
        }
        LOG.info("  All report files were written");
        if (ex != null)
            throw ex;
    }

    private void startServer(PerfTestParams params, Map<String, Object> env) throws Exception
    {
        Server server = new Server();

//        server.setDumpBeforeStop(true);

//        MBeanContainer mbContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
//        server.addBean(mbContainer);

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        if (params.isTlsEnabled())
        {
            SecureRequestCustomizer customizer = new SecureRequestCustomizer();
            customizer.setSniHostCheck(false);
            httpConfiguration.addCustomizer(customizer);
        }

        ConnectionFactory http;
        if (params.getHttpVersion() == HttpVersion.HTTP_2)
            http = new HTTP2CServerConnectionFactory(httpConfiguration);
        else
            http = new HttpConnectionFactory(httpConfiguration);

        List<ConnectionFactory> connectionFactories = new ArrayList<>();
        if (params.isTlsEnabled())
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
        serverConnector.setPort(params.getServerPort());

        server.addConnector(serverConnector);

        LatencyRecorder latencyRecorder = new LatencyRecorder("perf.hlog");
        Handler latencyRecordingHandler = new ModernLatencyRecordingHandler(testedHandlerSupplier.get(), latencyRecorder);
//        StatisticsHandler statisticsHandler = new StatisticsHandler(latencyRecordingHandler);
//        server.setHandler(statisticsHandler);
        server.setHandler(latencyRecordingHandler);
        server.start();

//        env.put(StatisticsHandler.class.getName(), statisticsHandler);
        env.put(Recorder.class.getName(), List.of(latencyRecorder));
        env.put(CompletableFuture.class.getName(), CompletableFuture.completedFuture(null));
        env.put(Server.class.getName(), server);
    }


    private void stopServer(Map<String, Object> env) throws Exception
    {
        ((Server)env.get(Server.class.getName())).stop();
//        StatisticsHandler statisticsHandler = (StatisticsHandler)env.get(StatisticsHandler.class.getName());
//        try (PrintWriter printWriter = new PrintWriter("StatisticsHandler.txt"))
//        {
//            statisticsHandler.dump(printWriter);
//        }
    }

    private void runLoadGenerator(PerfTestParams perfTestParams, Map<String, Object> env) throws Exception
    {
        LatencyRecorder latencyRecorder = new LatencyRecorder("perf.hlog");
        ResponseTimeListener responseTimeListener = new ResponseTimeListener(latencyRecorder);
        ResponseStatusListener responseStatusListener = new ResponseStatusListener("http-client-statuses.log");
        env.put(Recorder.class.getName(), List.of(latencyRecorder, responseStatusListener));

        URI serverUri = perfTestParams.getServerUri();
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(serverUri.getScheme())
            .host(serverUri.getHost())
            .port(serverUri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(perfTestParams.getWarmupDuration().plus(perfTestParams.getRunDuration()).toSeconds(), TimeUnit.SECONDS)
            .threads(1)
            .rateRampUpPeriod(perfTestParams.getWarmupDuration().toSeconds() / 2)
            .resourceRate(perfTestParams.getLoaderRate())
            .resource(new Resource(serverUri.getPath()))
            .resourceListener(responseTimeListener)
            .listener(responseTimeListener)
            .resourceListener(responseStatusListener)
            .listener(responseStatusListener)
            ;

        if (perfTestParams.getHttpVersion() == HttpVersion.HTTP_2)
        {
            builder.httpClientTransportBuilder(new HTTP2ClientTransportBuilder());
        }

        LoadGenerator loadGenerator = builder.build();
        LOG.info("load generation begin with client '{}'", HttpClient.USER_AGENT);
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

    private void runProbeGenerator(PerfTestParams perfTestParams, Map<String, Object> env) throws Exception
    {
        LatencyRecorder latencyRecorder = new LatencyRecorder("perf.hlog");
        ResponseTimeListener responseTimeListener = new ResponseTimeListener(latencyRecorder);
        ResponseStatusListener responseStatusListener = new ResponseStatusListener("http-client-statuses.log");
        env.put(Recorder.class.getName(), List.of(latencyRecorder, responseStatusListener));

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
            ;

        if (perfTestParams.getHttpVersion() == HttpVersion.HTTP_2)
        {
            builder.httpClientTransportBuilder(new HTTP2ClientTransportBuilder());
        }

        LoadGenerator loadGenerator = builder.build();
        LOG.info("probe generation begin with client '{}'", HttpClient.USER_AGENT);
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
