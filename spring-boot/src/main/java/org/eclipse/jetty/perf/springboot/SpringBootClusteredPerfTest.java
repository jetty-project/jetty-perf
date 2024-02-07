package org.eclipse.jetty.perf.springboot;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.perf.test.ClusteredTestContext;
import org.eclipse.jetty.perf.test.Jetty12ClusteredPerfTest;
import org.eclipse.jetty.perf.test.PerfTestParams;
import org.eclipse.jetty.perf.util.Recorder;
import org.eclipse.jetty.perf.util.SerializableConsumer;
import org.eclipse.jetty.perf.util.SerializableSupplier;
import org.eclipse.jetty.server.Handler;
import org.mortbay.jetty.orchestrator.ClusterTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

public class SpringBootClusteredPerfTest extends Jetty12ClusteredPerfTest
{
    private static final Logger LOG = LoggerFactory.getLogger(SpringBootClusteredPerfTest.class);

    protected SpringBootClusteredPerfTest(String testName, Path reportRootPath, PerfTestParams perfTestParams, SerializableSupplier<Handler> testedHandlerSupplier, SerializableConsumer<PerfTestParams> perfTestParamsCustomizer) throws Exception
    {
        super(testName, reportRootPath, perfTestParams, testedHandlerSupplier, perfTestParamsCustomizer);
    }

    public static void runTest(ClusteredTestContext clusteredTestContext) throws Exception
    {
        runTest(clusteredTestContext, new PerfTestParams(), p ->
        {});
    }

    public static void runTest(ClusteredTestContext clusteredTestContext, PerfTestParams perfTestParams, SerializableConsumer<PerfTestParams> perfTestParamsCustomizer) throws Exception
    {
        try (SpringBootClusteredPerfTest clusteredPerfTest = new SpringBootClusteredPerfTest(clusteredTestContext.getTestName(), clusteredTestContext.getReportRootPath(), perfTestParams, null, perfTestParamsCustomizer))
        {
            clusteredPerfTest.execute();
        }
    }

    @Override
    protected void startServer(PerfTestParams perfTestParams, ClusterTools clusterTools) throws Exception
    {
        // TODO how to pass PerfTestParams to Spring?
        JettyPerfSpringBootApplication.perfTestParams = perfTestParams;
        ConfigurableApplicationContext applicationContext = SpringApplication.run(JettyPerfSpringBootApplication.class);
        JettyCustomizer jettyCustomizer = applicationContext.getBean("jettyCustomizer", JettyCustomizer.class);

        Map<String, Object> env = clusterTools.nodeEnvironment();
        env.put(ApplicationContext.class.getName(), applicationContext);
        env.put(Recorder.class.getName(), List.of(new Recorder()
        {
            @Override
            public void startRecording()
            {
            }

            @Override
            public void stopRecording()
            {
                try
                {
                    try (PrintWriter printWriter = new PrintWriter("ServerDump.txt"))
                    {
                        jettyCustomizer.getServer().dump(printWriter);
                    }

                    // The server dump has been taken with the clients still connected, they can now be freed to complete their shutdown.
                    clusterTools.barrier("clients-end-barrier", perfTestParams.getParticipantCount() - 1).await(30, TimeUnit.SECONDS);
                }
                catch (Exception e)
                {
                    LOG.error("Error writing server reports", e);
                }
            }
        }, jettyCustomizer.getLatencyRecorder()));
        env.put(CompletableFuture.class.getName(), CompletableFuture.completedFuture(null));
    }

    @Override
    protected void stopServer(PerfTestParams perfTestParams, ClusterTools clusterTools) throws Exception
    {
        ConcurrentMap<String, Object> env = clusterTools.nodeEnvironment();
        ApplicationContext applicationContext = (ApplicationContext)env.get(ApplicationContext.class.getName());
        SpringApplication.exit(applicationContext);
    }
}
