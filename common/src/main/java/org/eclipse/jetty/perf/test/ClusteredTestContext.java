package org.eclipse.jetty.perf.test;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.jetty.perf.util.OutputCapturer;

public class ClusteredTestContext implements Closeable
{
    private final String testName;
    private final Path reportRootPath;
    private final OutputCapturer outputCapturer;

    public ClusteredTestContext(Class<?> testClass, Method testMethod, String... testParameterNames) throws Exception
    {
        // Generate test name
        testName = testClass.getSimpleName() + "_" + testMethod.getName();

        // Create report folder
        reportRootPath = createReportRootPath(testName, testParameterNames);

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

    private static Path createReportRootPath(String testName, String... testParameterNames) throws IOException
    {
        Path reportsRoot = FileSystems.getDefault().getPath("target", "reports");
        Path reportRootPath = reportsRoot.resolve(testName);
        for (String subPath : testParameterNames)
        {
            reportRootPath = reportRootPath.resolve(subPath);
        }

        // if report folder already exists, rename it out of the way
        if (Files.isDirectory(reportRootPath))
        {
            Path parentFolder = reportsRoot.resolve(testName);
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date(Files.getLastModifiedTime(parentFolder).toMillis()));
            Path newFolder = parentFolder.getParent().resolve(parentFolder.getFileName().toString() + "_" + timestamp);
            Files.move(parentFolder, newFolder);
        }

        Files.createDirectories(reportRootPath);
        return reportRootPath;
    }
}
