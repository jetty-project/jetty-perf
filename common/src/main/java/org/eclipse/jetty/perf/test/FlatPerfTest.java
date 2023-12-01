package org.eclipse.jetty.perf.test;

import java.nio.file.Path;

import org.eclipse.jetty.perf.util.OutputCapturer;
import org.eclipse.jetty.perf.util.ReportUtil;
import org.eclipse.jetty.perf.util.SerializableSupplier;
import org.eclipse.jetty.server.Handler;

public class FlatPerfTest
{
    public static void runTest(String testName, SerializableSupplier<Handler> testedHandlerSupplier) throws Exception
    {
        PerfTestParams params = new PerfTestParams();
        Path reportRootPath = ReportUtil.createReportRootPath(testName, params.toString());
        try (OutputCapturer ignore = new OutputCapturer(reportRootPath))
        {
            try (ClusteredPerfTest clusteredPerfTest = new ClusteredPerfTest(testName, params, testedHandlerSupplier, reportRootPath))
            {
                clusteredPerfTest.execute();
            }
        }
    }
}
