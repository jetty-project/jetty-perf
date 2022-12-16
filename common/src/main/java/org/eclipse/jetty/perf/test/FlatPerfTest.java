package org.eclipse.jetty.perf.test;

import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.jetty.perf.util.OutputCapturer;
import org.eclipse.jetty.perf.util.ReportUtil;
import org.eclipse.jetty.perf.util.SerializableSupplier;
import org.eclipse.jetty.server.Handler;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;

import static org.eclipse.jetty.perf.assertions.Assertions.assertHttpClientStatuses;
import static org.eclipse.jetty.perf.assertions.Assertions.assertP99Latency;
import static org.eclipse.jetty.perf.assertions.Assertions.assertThroughput;

public class FlatPerfTest
{
    public static boolean runTest(String testName, PerfTestParams params, Duration warmupDuration, Duration runDuration, SerializableSupplier<Handler> testedHandlerSupplier) throws Exception
    {
        Path reportRootPath = ReportUtil.createReportRootPath(testName, params.toString());
        try (OutputCapturer ignore = new OutputCapturer(reportRootPath))
        {
            try (ClusteredPerfTest clusteredPerfTest = new ClusteredPerfTest(testName, params, warmupDuration, runDuration, testedHandlerSupplier, reportRootPath))
            {
                clusteredPerfTest.execute();
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
}
