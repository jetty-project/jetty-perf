package org.eclipse.jetty.perf;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import org.mortbay.jetty.orchestrator.ClusterTools;
import org.mortbay.jetty.orchestrator.NodeArray;
import org.mortbay.jetty.orchestrator.NodeArrayFuture;
import org.mortbay.jetty.orchestrator.NodeJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.perf.util.ReportUtil.generateReport;

public class HttpPerfTest implements Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(HttpPerfTest.class);

    private static Stream<PerfTestParams> params()
    {
        long now = System.currentTimeMillis();
        return Stream.of(
            new PerfTestParams(now, PerfTestParams.Protocol.http),
            new PerfTestParams(now, PerfTestParams.Protocol.https),
            new PerfTestParams(now, PerfTestParams.Protocol.h2c),
            new PerfTestParams(now, PerfTestParams.Protocol.h2)
        );
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testPerf(PerfTestParams params) throws Exception
    {
        Path reportRootPath = FileSystems.getDefault().getPath("target", "reports", params.getReportPath());
        try (OutputCapturingCluster outputCapturingCluster = new OutputCapturingCluster(params.getClusterConfiguration(), reportRootPath.resolve("outerr.log")))
        {
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

            serverArray.executeOnAll(tools -> startServer(params, tools)).get(30, TimeUnit.SECONDS);

            LOG.info("Warming up...");
            NodeArrayFuture warmupLoaders = loadersArray.executeOnAll(tools -> runLoadGenerator(params, serverUri, params.getWarmupDuration(), false, false));
            NodeArrayFuture warmupProbe = probeArray.executeOnAll(tools -> runLoadGenerator(params, serverUri, params.getWarmupDuration(), false, false));
            warmupLoaders.get(params.getWarmupDuration().toSeconds() + 30, TimeUnit.SECONDS);
            warmupProbe.get(params.getWarmupDuration().toSeconds() + 30, TimeUnit.SECONDS);

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
                    Server server = (Server)tools.nodeEnvironment().get(Server.class.getName());
                    server.stop();
                }
            });

            NodeArrayFuture loadersFuture = loadersArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor ignore = new ConfigurableMonitor(params.getMonitoredItems()))
                {
                    tools.barrier("run-start-barrier", participantCount).await();
                    runLoadGenerator(params, serverUri, params.getRunDuration(), true, true);
                    tools.barrier("run-end-barrier", participantCount).await();
                }
            });

            NodeArrayFuture probeFuture = probeArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor ignore = new ConfigurableMonitor(params.getMonitoredItems()))
                {
                    tools.barrier("run-start-barrier", participantCount).await();
                    runProbeGenerator(params, serverUri, params.getRunDuration());
                    tools.barrier("run-end-barrier", participantCount).await();
                }
            });

            // signal all participants to start
            cluster.tools().barrier("run-start-barrier", participantCount).await(30, TimeUnit.SECONDS);
            // signal all participants to stop monitoring
            cluster.tools().barrier("run-end-barrier", participantCount).await(params.getRunDuration().toSeconds() + 30, TimeUnit.SECONDS);

            // wait for all report files to be written
            serverFuture.get(30, TimeUnit.SECONDS);
            loadersFuture.get(30, TimeUnit.SECONDS);
            probeFuture.get(30, TimeUnit.SECONDS);

            LOG.info("Generating report...");
            generateReport(reportRootPath, params.getClusterConfiguration(), cluster);

            long after = System.nanoTime();
            LOG.info("Done; elapsed={} ms", TimeUnit.NANOSECONDS.toMillis(after - before));
        }
    }

    private void startServer(PerfTestParams params, ClusterTools tools) throws Exception
    {
        Server server = new Server();

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

        ServerConnector serverConnector = new ServerConnector(server, connectionFactories.toArray(new ConnectionFactory[0]));
        serverConnector.setPort(params.getServerPort());

        LatencyRecordingChannelListener listener = new LatencyRecordingChannelListener();
        serverConnector.addBean(listener);

        server.addConnector(serverConnector);

        server.setHandler(new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1)));
        server.start();

        tools.nodeEnvironment().put(LatencyRecordingChannelListener.class.getName(), listener);
        tools.nodeEnvironment().put(Server.class.getName(), server);
    }

    private void runLoadGenerator(PerfTestParams params, URI serverUri, Duration duration, boolean generatePerfHisto, boolean generateStatuses) throws IOException
    {
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(serverUri.getScheme())
            .host(serverUri.getHost())
            .port(serverUri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(duration.toSeconds(), TimeUnit.SECONDS)
            .threads(2)
            .rateRampUpPeriod(0)
            .resourceRate(50_000)
            .resource(new Resource(serverUri.getPath()));

        if (params.getProtocol().getVersion() == PerfTestParams.HttpVersion.HTTP2)
        {
            builder.httpClientTransportBuilder(new HTTP2ClientTransportBuilder());
        }
        if (generatePerfHisto)
        {
            ResponseTimeListener responseTimeListener = new ResponseTimeListener();
            builder.resourceListener(responseTimeListener);
            builder.listener(responseTimeListener);
        }
        if (generateStatuses)
        {
            ResponseStatusListener responseStatusListener = new ResponseStatusListener();
            builder.resourceListener(responseStatusListener);
            builder.listener(responseStatusListener);
        }

        LoadGenerator loadGenerator = builder.build();
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

    private void runProbeGenerator(PerfTestParams params, URI serverUri, Duration duration) throws IOException
    {
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(serverUri.getScheme())
            .host(serverUri.getHost())
            .port(serverUri.getPort())
            .sslContextFactory(new SslContextFactory.Client(true))
            .runFor(duration.toSeconds(), TimeUnit.SECONDS)
            .rateRampUpPeriod(0)
            .resourceRate(100)
            .resource(new Resource(serverUri.getPath()))
            .resourceListener(new ResponseTimeListener())
            .resourceListener(new ResponseStatusListener());

        if (params.getProtocol().getVersion() == PerfTestParams.HttpVersion.HTTP2)
        {
            builder.httpClientTransportBuilder(new HTTP2ClientTransportBuilder());
        }

        LoadGenerator loadGenerator = builder.build();
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
