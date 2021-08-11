package org.eclipse.jetty.perf;

import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.management.remote.JMXServiceURL;

import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.perf.util.PerfTestParams;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mortbay.jetty.load.generator.HTTP2ClientTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.NodeArray;
import org.mortbay.jetty.orchestrator.NodeArrayFuture;
import org.mortbay.jetty.orchestrator.NodeJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jetty.perf.handler.AsyncHandler;
import org.eclipse.jetty.perf.histogram.loader.ResponseStatusListener;
import org.eclipse.jetty.perf.histogram.loader.ResponseTimeListener;
import org.eclipse.jetty.perf.histogram.server.LatencyRecordingChannelListener;
import org.eclipse.jetty.perf.monitoring.ConfigurableMonitor;

import static org.eclipse.jetty.perf.util.ReportUtil.download;
import static org.eclipse.jetty.perf.util.ReportUtil.transformHisto;

public class HttpPerfTest implements Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(HttpPerfTest.class);

    private static Stream<PerfTestParams> params()
    {
        return Stream.of(
            new PerfTestParams(PerfTestParams.HttpVersion.HTTP11, false),
//            new PerfTestParams(PerfTestParams.HttpVersion.HTTP11, true),
            new PerfTestParams(PerfTestParams.HttpVersion.HTTP2, false)
//            new PerfTestParams(PerfTestParams.HttpVersion.HTTP2, true)
        );
    }

    @ParameterizedTest
    @MethodSource("params")
    public void testPerf(PerfTestParams params) throws Exception
    {
        LOG.info("Initializing...");

        try (Cluster cluster = new Cluster(params.getClusterConfiguration()))
        {
            NodeArray serverArray = cluster.nodeArray("server");
            NodeArray loadersArray = cluster.nodeArray("loaders");
            NodeArray probeArray = cluster.nodeArray("probe");
            int participantCount = params.getClusterConfiguration().nodeArrays().stream().mapToInt(na -> na.nodes().size()).sum() + 1; // + 1 b/c of the test itself

            NodeJob logSystemProps = tools -> LOG.info("JVM version: '{}', OS name: '{}', OS arch: '{}'", System.getProperty("java.vm.version"), System.getProperty("os.name"), System.getProperty("os.arch"));
            serverArray.executeOnAll(logSystemProps).get();
            loadersArray.executeOnAll(logSystemProps).get();
            probeArray.executeOnAll(logSystemProps).get();

            serverArray.executeOnAll(tools ->
            {
                Server server = new Server();
                tools.nodeEnvironment().put(Server.class.getName(), server);

                MBeanContainer mbContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
                server.addBean(mbContainer);
                server.addBean(LoggerFactory.getILoggerFactory());

                ConnectorServer connectorServer = new ConnectorServer(new JMXServiceURL("service:jmx:rmi://localhost:1099/jndi/rmi://localhost:1099/jmxrmi"), "org.eclipse.jetty:name=rmiconnectorserver");
                server.addBean(connectorServer);

                HttpConfiguration httpConfiguration = new HttpConfiguration();
                SecureRequestCustomizer customizer = new SecureRequestCustomizer();
                customizer.setSniHostCheck(false);
                httpConfiguration.addCustomizer(customizer);
                ConnectionFactory http;
                if (params.getHttpVersion() == PerfTestParams.HttpVersion.HTTP2)
                    http = new HTTP2CServerConnectionFactory(httpConfiguration);
                else
                    http = new HttpConnectionFactory(httpConfiguration);

                ServerConnector serverConnector = new ServerConnector(server, http);
                serverConnector.setPort(8080);
                server.addConnector(serverConnector);
                server.setHandler(new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1)));
                server.start();
            }).get(30, TimeUnit.SECONDS);

            LOG.info("Warming up...");
            URI serverUri = new URI("http" + (params.isSecure() ? "s" : "") + "://" + serverArray.hostnameOf(serverArray.ids().stream().findFirst().orElseThrow()) + ":8080");
            NodeArrayFuture warmupLoaders = loadersArray.executeOnAll(tools -> runLoadGenerator(params, serverUri, params.getWarmupDuration()));
            NodeArrayFuture warmupProbe = probeArray.executeOnAll(tools -> runLoadGenerator(params, serverUri, params.getWarmupDuration()));
            warmupLoaders.get(params.getWarmupDuration().toSeconds() + 30, TimeUnit.SECONDS);
            warmupProbe.get(params.getWarmupDuration().toSeconds() + 30, TimeUnit.SECONDS);

            LOG.info("Running...");
            long before = System.nanoTime();

            NodeArrayFuture serverFuture = serverArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor m = new ConfigurableMonitor(params.getMonitoredItems()))
                {
                    Server server = (Server)tools.nodeEnvironment().get(Server.class.getName());
                    Connector serverConnector = server.getConnectors()[0];
                    LatencyRecordingChannelListener listener = new LatencyRecordingChannelListener("server.hlog");
                    LifeCycle.start(listener);
                    serverConnector.addBean(listener);
                    tools.barrier("run-start-barrier", participantCount).await();
                    tools.barrier("run-end-barrier", participantCount).await();
                    LifeCycle.stop(listener);
                    server.stop();
                }
            });

            NodeArrayFuture loadersFuture = loadersArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor m = new ConfigurableMonitor(params.getMonitoredItems()))
                {
                    tools.barrier("run-start-barrier", participantCount).await();
                    runLoadGenerator(params, serverUri, params.getRunDuration(), "loader.hlog", "status.txt");
                    tools.barrier("run-end-barrier", participantCount).await();
                }
            });

            NodeArrayFuture probeFuture = probeArray.executeOnAll(tools ->
            {
                try (ConfigurableMonitor m = new ConfigurableMonitor(params.getMonitoredItems()))
                {
                    tools.barrier("run-start-barrier", participantCount).await();
                    runProbeGenerator(params, serverUri, params.getRunDuration(), "probe.hlog", "status.txt");
                    tools.barrier("run-end-barrier", participantCount).await();
                }
            });

            // signal all participants to start
            cluster.tools().barrier("run-start-barrier", participantCount).await(30, TimeUnit.SECONDS);
            // signal all participants to stop monitoring
            cluster.tools().barrier("run-end-barrier", participantCount).await(params.getRunDuration().toSeconds() + 30, TimeUnit.SECONDS);

            // wait for all monitoring reports to be written
            serverFuture.get(30, TimeUnit.SECONDS);
            loadersFuture.get(30, TimeUnit.SECONDS);
            probeFuture.get(30, TimeUnit.SECONDS);

            LOG.info("Downloading reports...");
            Path reportRoot = FileSystems.getDefault().getPath("target", "report", params.getReportPath());
            // download servers FGs & transform histograms
            download(serverArray, reportRoot.resolve("server"));
            transformHisto(serverArray, reportRoot.resolve("server"), "server.hlog");
            // download loaders FGs & transform histograms
            download(loadersArray, reportRoot.resolve("loader"));
            transformHisto(loadersArray, reportRoot.resolve("loader"), "loader.hlog");
            // download probes FGs & transform histograms
            download(probeArray, reportRoot.resolve("probe"));
            transformHisto(probeArray, reportRoot.resolve("probe"), "probe.hlog");

            long after = System.nanoTime();
            LOG.info("Done; elapsed={} ms", TimeUnit.NANOSECONDS.toMillis(after - before));
        }
    }

    private void runLoadGenerator(PerfTestParams params, URI serverUri, Duration duration) throws IOException
    {
        runLoadGenerator(params, serverUri, duration, null, null);
    }

    private void runLoadGenerator(PerfTestParams params, URI serverUri, Duration duration, String histogramFilename, String statusFilename) throws IOException
    {
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(serverUri.getScheme())
            .host(serverUri.getHost())
            .port(serverUri.getPort())
            .runFor(duration.toSeconds(), TimeUnit.SECONDS)
            .threads(2)
            .rateRampUpPeriod(0)
            .resourceRate(50_000)
            .resource(new Resource(serverUri.getPath()));

        if (params.getHttpVersion() == PerfTestParams.HttpVersion.HTTP2)
        {
            builder.httpClientTransportBuilder(new HTTP2ClientTransportBuilder());
        }
        if (histogramFilename != null)
        {
            ResponseTimeListener responseTimeListener = new ResponseTimeListener(histogramFilename);
            builder.resourceListener(responseTimeListener);
            builder.listener(responseTimeListener);
        }
        if (statusFilename != null)
        {
            ResponseStatusListener responseStatusListener = new ResponseStatusListener(statusFilename);
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

    private void runProbeGenerator(PerfTestParams params, URI serverUri, Duration duration, String histogramFilename, String statusFilename) throws IOException
    {
        LoadGenerator.Builder builder = LoadGenerator.builder()
            .scheme(serverUri.getScheme())
            .host(serverUri.getHost())
            .port(serverUri.getPort())
            .runFor(duration.toSeconds(), TimeUnit.SECONDS)
            .rateRampUpPeriod(0)
            .resourceRate(100)
            .resource(new Resource(serverUri.getPath()))
            .resourceListener(new ResponseTimeListener(histogramFilename))
            .resourceListener(new ResponseStatusListener(statusFilename));

        if (params.getHttpVersion() == PerfTestParams.HttpVersion.HTTP2)
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
