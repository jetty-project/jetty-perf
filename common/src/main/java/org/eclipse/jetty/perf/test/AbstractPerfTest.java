package org.eclipse.jetty.perf.test;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.perf.handler.LatencyRecordingHandler;
import org.eclipse.jetty.perf.histogram.loader.ResponseStatusListener;
import org.eclipse.jetty.perf.histogram.loader.ResponseTimeListener;
import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;
import org.eclipse.jetty.perf.util.IOUtil;
import org.eclipse.jetty.perf.util.LatencyRecorder;
import org.eclipse.jetty.perf.util.OutputCapturingCluster;
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
import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.perf.assertions.Assertions.assertHttpClientStatuses;
import static org.eclipse.jetty.perf.assertions.Assertions.assertP99Latency;
import static org.eclipse.jetty.perf.assertions.Assertions.assertThroughput;
import static org.eclipse.jetty.perf.util.ReportUtil.generateReport;

public abstract class AbstractPerfTest implements Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(AbstractPerfTest.class);

    private static String generateId()
    {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String className = stackTrace[3].getClassName();
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        return simpleClassName + "_" + stackTrace[3].getMethodName();
    }

    public boolean runTest(PerfTestParams params, Duration warmupDuration, Duration runDuration) throws Exception
    {
        try (OutputCapturingCluster outputCapturingCluster = new OutputCapturingCluster(params.getClusterConfiguration(), generateId(), params.toString()))
        {
            Path reportRootPath = outputCapturingCluster.getReportRootPath();
            Cluster cluster = outputCapturingCluster.getCluster();
            NodeArray serverArray = cluster.nodeArray("server");
            NodeArray loadersArray = cluster.nodeArray("loaders");
            NodeArray probeArray = cluster.nodeArray("probe");
            int participantCount = params.getParticipantCount();

            NodeJob logSystemProps = tools -> LOG.info("JVM version: '{}', OS name: '{}', OS arch: '{}'", System.getProperty("java.vm.version"), System.getProperty("os.name"), System.getProperty("os.arch"));
            serverArray.executeOnAll(logSystemProps).get();
            loadersArray.executeOnAll(logSystemProps).get();
            probeArray.executeOnAll(logSystemProps).get();

            // start the server and the generators
            serverArray.executeOnAll(tools -> startServer(params, tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);
            loadersArray.executeOnAll(tools -> runLoadGenerator(params, warmupDuration, runDuration, tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);
            probeArray.executeOnAll(tools -> runProbeGenerator(params, warmupDuration, runDuration, tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);

            LOG.info("Warming up...");
            Thread.sleep(warmupDuration.toMillis());

            LOG.info("Running...");
            long before = System.nanoTime();

            NodeArrayFuture serverFuture = serverArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor ignore = new ConfigurableMonitor(params.getMonitoredItems()))
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
                try (ConfigurableMonitor ignore = new ConfigurableMonitor(params.getMonitoredItems()))
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
                try (ConfigurableMonitor ignore = new ConfigurableMonitor(params.getMonitoredItems()))
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
                generateReport(reportRootPath, params.getClusterConfiguration(), cluster);

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

            NodeArrayConfiguration serverCfg = params.getClusterConfiguration().nodeArrays().stream().filter(nac -> nac.id().equals("server")).findAny().orElseThrow();
            NodeArrayConfiguration loadersCfg = params.getClusterConfiguration().nodeArrays().stream().filter(nac -> nac.id().equals("loaders")).findAny().orElseThrow();
            NodeArrayConfiguration probeCfg = params.getClusterConfiguration().nodeArrays().stream().filter(nac -> nac.id().equals("probe")).findAny().orElseThrow();
            int loadersCount = params.getClusterConfiguration().nodeArrays().stream().filter(nac -> nac.id().equals("loaders")).mapToInt(nac -> nac.nodes().size()).sum();
            long totalLoadersRequestCount = params.getLoaderRate() * loadersCount * runDuration.toSeconds();
            long totalProbeRequestCount = params.getProbeRate() * runDuration.toSeconds();

            boolean succeeded = true;

            System.out.println(" Asserting loaders");
            // assert loaders did not get too many HTTP errors
            succeeded &= assertHttpClientStatuses(reportRootPath, loadersCfg, runDuration.toSeconds() * 2); // max 2 errors per second on avg
            // assert loaders had a given throughput
            succeeded &= assertThroughput(reportRootPath, loadersCfg, totalLoadersRequestCount, 1);

            System.out.println(" Asserting probe");
            // assert probe did not get too many HTTP errors
            succeeded &= assertHttpClientStatuses(reportRootPath, probeCfg, runDuration.toSeconds() * 2); // max 2 errors per second on avg
            // assert probe had a given throughput and max latency
            succeeded &= assertThroughput(reportRootPath, probeCfg, totalProbeRequestCount, 1);
            // assert probe had a given max latency
            succeeded &= assertP99Latency(reportRootPath, probeCfg, params.getExpectedP99ProbeLatency(), params.getExpectedP99ErrorMargin(), 2);

            System.out.println(" Asserting server");
            // assert server had a given throughput
            succeeded &= assertThroughput(reportRootPath, serverCfg, totalLoadersRequestCount, 1);
            // assert server had a given max latency
            succeeded &= assertP99Latency(reportRootPath, serverCfg, params.getExpectedP99ServerLatency(), params.getExpectedP99ErrorMargin(), 2);

            return succeeded;
        }
    }

    private void startServer(PerfTestParams params, Map<String, Object> env) throws Exception
    {
        Server server = new Server();

//        MBeanContainer mbContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
//        server.addBean(mbContainer);

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        if (params.getProtocol().isSecure())
        {
            SecureRequestCustomizer customizer = new SecureRequestCustomizer();
            customizer.setSniHostCheck(false);
            httpConfiguration.addCustomizer(customizer);
        }

        ConnectionFactory http;
        if (params.getProtocol().getVersion() == PerfTestParams.HttpVersion.HTTP2)
            http = new HTTP2CServerConnectionFactory(httpConfiguration);
        else
            http = new HttpConnectionFactory(httpConfiguration);

        List<ConnectionFactory> connectionFactories = new ArrayList<>();
        if (params.getProtocol().isSecure())
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

        // register LatencyRecorder on the server to get it lifecycled such as the recoding is stopped with the server
        LatencyRecorder latencyRecorder = new LatencyRecorder("perf.hlog");
        server.addBean(latencyRecorder);

        server.addConnector(serverConnector);

        LatencyRecordingHandler latencyRecordingHandler = new LatencyRecordingHandler(latencyRecorder);
        latencyRecordingHandler.setHandler(createHandler());
        server.setHandler(latencyRecordingHandler);
        server.start();

        env.put(LatencyRecorder.class.getName(), latencyRecorder);
        env.put(Server.class.getName(), server);
    }

    protected abstract Handler createHandler();

    private void stopServer(Map<String, Object> env) throws Exception
    {
        ((Server)env.get(Server.class.getName())).stop();
    }

    private void runLoadGenerator(PerfTestParams params, Duration warmupDuration, Duration runDuration, Map<String, Object> env) throws Exception
    {
        URI serverUri = params.getServerUri();
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
            .resourceRate(params.getLoaderRate())
            .resource(new Resource(serverUri.getPath()))
            .resourceListener(responseTimeListener)
            .listener(responseTimeListener)
            .resourceListener(responseStatusListener)
            .listener(responseStatusListener)
            ;

        if (params.getProtocol().getVersion() == PerfTestParams.HttpVersion.HTTP2)
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

    private void runProbeGenerator(PerfTestParams params, Duration warmupDuration, Duration runDuration, Map<String, Object> env) throws Exception
    {
        URI serverUri = params.getServerUri();
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
            .resourceRate(params.getProbeRate())
            .resource(new Resource(serverUri.getPath()))
            .resourceListener(responseTimeListener)
            .listener(responseTimeListener)
            .resourceListener(responseStatusListener)
            .listener(responseStatusListener)
            ;

        if (params.getProtocol().getVersion() == PerfTestParams.HttpVersion.HTTP2)
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