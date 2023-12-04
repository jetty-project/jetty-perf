package org.eclipse.jetty.perf.test;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;

import org.eclipse.jetty.perf.util.OutputCapturer;
import org.eclipse.jetty.perf.util.ReportUtil;
import org.junit.jupiter.api.TestInfo;

public class ClusteredTestContext implements Closeable
{
    private final String testName;
    private final Path reportRootPath;
    private final OutputCapturer outputCapturer;

    public ClusteredTestContext(TestInfo testInfo) throws Exception
    {
        // Generate test name
        String className = testInfo.getTestClass().orElseThrow().getName();
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        String methodName = testInfo.getTestMethod().orElseThrow().getName();
        testName = simpleClassName + "_" + methodName;

        // Create report folder
        reportRootPath = ReportUtil.createReportRootPath(testName);

        // Capture stdout and stderr
        outputCapturer = new OutputCapturer(reportRootPath);
    }

    public ClusteredTestContext(Class<?> testClass, Method testMethod) throws Exception
    {
        // Generate test name
        testName = testClass.getSimpleName() + "_" + testMethod.getName();

        // Create report folder
        reportRootPath = ReportUtil.createReportRootPath(testName);

        // Capture stdout and stderr
        outputCapturer = new OutputCapturer(reportRootPath);
    }

    public String getTestName()
    {
        return testName;
    }

    public Path getReportRootPath()
    {
        return reportRootPath;
    }

    @Override
    public void close() throws IOException
    {
        outputCapturer.close();
    }
}
