package org.eclipse.jetty.perf;

import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.perf.handler.AsyncHandler;
import org.eclipse.jetty.perf.histogram.loader.ResponseStatusListener;
import org.eclipse.jetty.perf.histogram.loader.ResponseTimeListener;
import org.eclipse.jetty.perf.histogram.server.LatencyRecordingChannelListener;
import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;
import org.eclipse.jetty.perf.util.OutputCapturingCluster;
import org.eclipse.jetty.perf.util.PerfTestParams;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class HttpPerfTest implements Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(HttpPerfTest.class);

    private static final Duration WARMUP_DURATION = Duration.ofSeconds(60);
    private static final Duration RUN_DURATION = Duration.ofSeconds(180);

    private static Stream<PerfTestParams> params()
    {
        return Stream.of(
            new PerfTestParams(PerfTestParams.Protocol.http),
            new PerfTestParams(PerfTestParams.Protocol.https),
            new PerfTestParams(PerfTestParams.Protocol.h2c),
            new PerfTestParams(PerfTestParams.Protocol.h2)
        );
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testPerf(PerfTestParams params) throws Exception
    {
        try (OutputCapturingCluster outputCapturingCluster = new OutputCapturingCluster(params.getClusterConfiguration(), params.toString()))
        {
            Path reportRootPath = outputCapturingCluster.getReportRootPath();
            Cluster cluster = outputCapturingCluster.getCluster();
            NodeArray serverArray = cluster.nodeArray("server");
            NodeArray loadersArray = cluster.nodeArray("loaders");
            NodeArray probeArray = cluster.nodeArray("probe");
            int participantCount = params.getParticipantCount();
            URI serverUri = params.getServerUri();

            NodeJob logSystemProps = tools -> LOG.info("JVM version: '{}', OS name: '{}', OS arch: '{}'", System.getProperty("java.vm.version"), System.getProperty("os.name"), System.getProperty("os.arch"));
            serverArray.executeOnAll(logSystemProps).get();
            loadersArray.executeOnAll(logSystemProps).get();
            probeArray.executeOnAll(logSystemProps).get();

            // start the server and the generators
            serverArray.executeOnAll(tools -> startServer(params, tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);
            loadersArray.executeOnAll(tools -> runLoadGenerator(params, serverUri, tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);
            probeArray.executeOnAll(tools -> runProbeGenerator(params, serverUri, tools.nodeEnvironment())).get(30, TimeUnit.SECONDS);

            LOG.info("Warming up...");
            Thread.sleep(WARMUP_DURATION.toMillis());

            LOG.info("Running...");
            long before = System.nanoTime();

            NodeArrayFuture serverFuture = serverArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor ignore = new ConfigurableMonitor(params.getMonitoredItems()))
                {
                    LatencyRecordingChannelListener listener = (LatencyRecordingChannelListener)tools.nodeEnvironment().get(LatencyRecordingChannelListener.class.getName());
                    listener.startRecording();
                    tools.barrier("run-start-barrier", participantCount).await();
                    tools.barrier("run-end-barrier", participantCount).await();
                    listener.stopRecording();
                }
            });

            NodeArrayFuture loadersFuture = loadersArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor ignore = new ConfigurableMonitor(params.getMonitoredItems()))
                {
                    ResponseTimeListener responseTimeListener = (ResponseTimeListener)tools.nodeEnvironment().get(ResponseTimeListener.class.getName());
                    responseTimeListener.startRecording();
                    ResponseStatusListener responseStatusListener = (ResponseStatusListener)tools.nodeEnvironment().get(ResponseStatusListener.class.getName());
                    responseStatusListener.startRecording();
                    tools.barrier("run-start-barrier", participantCount).await();
                    tools.barrier("run-end-barrier", participantCount).await();
                    responseTimeListener.stopRecording();
                    responseStatusListener.stopRecording();
                    CompletableFuture<?> cf = (CompletableFuture<?>)tools.nodeEnvironment().get(CompletableFuture.class.getName());
                    cf.get();
                }
            });

            NodeArrayFuture probeFuture = probeArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor ignore = new ConfigurableMonitor(params.getMonitoredItems()))
                {
                    ResponseTimeListener responseTimeListener = (ResponseTimeListener)tools.nodeEnvironment().get(ResponseTimeListener.class.getName());
                    responseTimeListener.startRecording();
                    ResponseStatusListener responseStatusListener = (ResponseStatusListener)tools.nodeEnvironment().get(ResponseStatusListener.class.getName());
                    responseStatusListener.startRecording();
                    tools.barrier("run-start-barrier", participantCount).await();
                    tools.barrier("run-end-barrier", participantCount).await();
                    responseTimeListener.stopRecording();
                    responseStatusListener.stopRecording();
                    CompletableFuture<?> cf = (CompletableFuture<?>)tools.nodeEnvironment().get(CompletableFuture.class.getName());
                    cf.get();
                }
            });

            try
            {
                // signal all participants to start
                cluster.tools().barrier("run-start-barrier", participantCount).await(30, TimeUnit.SECONDS);
                // wait for the run duration
                Thread.sleep(RUN_DURATION.toMillis());
                // signal all participants to stop
                cluster.tools().barrier("run-end-barrier", participantCount).await(30, TimeUnit.SECONDS);

                // wait for all report files to be written
                serverFuture.get(30, TimeUnit.SECONDS);
                loadersFuture.get(30, TimeUnit.SECONDS);
                probeFuture.get(30, TimeUnit.SECONDS);

                // stop server
                serverArray.executeOnAll(tools -> ((Server)tools.nodeEnvironment().get(Server.class.getName())).stop()).get(30, TimeUnit.SECONDS);

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
            long totalLoadersRequestCount = params.getLoaderRate() * loadersCount * RUN_DURATION.toSeconds();
            long totalProbeRequestCount = params.getProbeRate() * RUN_DURATION.toSeconds();

            boolean succeeded = true;

            System.out.println(" Asserting loaders");
            // assert loaders did not get too many HTTP errors
            succeeded &= assertHttpClientStatuses(reportRootPath, loadersCfg, RUN_DURATION.toSeconds() * 2); // max 2 errors per second on avg
            // assert loaders had a given throughput
            succeeded &= assertThroughput(reportRootPath, loadersCfg, totalLoadersRequestCount, 1);

            System.out.println(" Asserting probe");
            // assert probe did not get too many HTTP errors
            succeeded &= assertHttpClientStatuses(reportRootPath, probeCfg, RUN_DURATION.toSeconds() * 2); // max 2 errors per second on avg
            // assert probe had a given throughput and max latency
            succeeded &= assertThroughput(reportRootPath, probeCfg, totalProbeRequestCount, 1);
            // assert probe had a given max latency
            succeeded &= assertP99Latency(reportRootPath, probeCfg, params.getExpectedP99ProbeLatency(), 25, 2);

            System.out.println(" Asserting server");
            // assert server had a given throughput
            succeeded &= assertThroughput(reportRootPath, serverCfg, totalLoadersRequestCount, 1);
            // assert server had a given max latency
            succeeded &= assertP99Latency(reportRootPath, serverCfg, params.getExpectedP99ServerLatency(), 25, 2);

            assertThat("Performance assertions failure for " + params, succeeded, is(true));
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
            String path = Paths.get(Objects.requireNonNull(getClass().getResource("/keystore.p12")).toURI()).toString();
            serverSslContextFactory.setKeyStorePath(path);
            serverSslContextFactory.setKeyStorePassword("storepwd");
            SslConnectionFactory ssl = new SslConnectionFactory(serverSslContextFactory, http.getProtocol());
            connectionFactories.add(ssl);
        }
        connectionFactories.add(http);

        ServerConnector serverConnector = new ServerConnector(server, 4, 24, connectionFactories.toArray(new ConnectionFactory[0]));
        serverConnector.setPort(params.getServerPort());

        LatencyRecordingChannelListener listener = new LatencyRecordingChannelListener();
        serverConnector.addBean(listener);

        server.addConnector(serverConnector);

        server.setHandler(new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1)));
        server.start();

        env.put(LatencyRecordingChannelListener.class.getName(), listener);
        env.put(Server.class.getName(), server);
    }

    private void runLoadGenerator(PerfTestParams params, URI serverUri, Map<String, Object> env) throws IOException
    {
        ResponseTimeListener responseTimeListener = new ResponseTimeListener();
        env.put(ResponseTimeListener.class.getName(), responseTimeListener);
        ResponseStatusListener responseStatusListener = new ResponseStatusListener();
        env.put(ResponseStatusListener.class.getName(), responseStatusListener);

        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(serverUri.getScheme())
            .host(serverUri.getHost())
            .port(serverUri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(WARMUP_DURATION.plus(RUN_DURATION).toSeconds(), TimeUnit.SECONDS)
            .threads(2)
            .rateRampUpPeriod(WARMUP_DURATION.toSeconds() / 2)
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

    private void runProbeGenerator(PerfTestParams params, URI serverUri, Map<String, Object> env) throws IOException
    {
        ResponseTimeListener responseTimeListener = new ResponseTimeListener();
        env.put(ResponseTimeListener.class.getName(), responseTimeListener);
        ResponseStatusListener responseStatusListener = new ResponseStatusListener();
        env.put(ResponseStatusListener.class.getName(), responseStatusListener);

        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(serverUri.getScheme())
            .host(serverUri.getHost())
            .port(serverUri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(WARMUP_DURATION.plus(RUN_DURATION).toSeconds(), TimeUnit.SECONDS)
            .threads(1)
            .rateRampUpPeriod(WARMUP_DURATION.toSeconds() / 2)
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
