package org.eclipse.jetty.perf.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutputCapturingCluster implements AutoCloseable
{
    private static final Logger LOG = LoggerFactory.getLogger(OutputCapturingCluster.class);

    private final OutErrCapture outErrCapture;
    private final Cluster cluster;
    private final Path reportRootPath;

    public OutputCapturingCluster(ClusterConfiguration clusterConfiguration, String... testParameterNames) throws Exception
    {
        this(clusterConfiguration, FileSystems.getDefault().getPath("target", "reports"), generateId(), testParameterNames);
    }

    private OutputCapturingCluster(ClusterConfiguration clusterConfiguration, Path reportsRoot, String testName, String... testParameterNames) throws Exception
    {
        Path reportRootPath = reportsRoot.resolve(testName);
        for (String subPath : testParameterNames)
            reportRootPath = reportRootPath.resolve(subPath);

        // if report folder already exists, rename it out of the way
        Path parentFolder = reportRootPath.getParent();
        if (Files.isDirectory(parentFolder))
        {
            String timestamp = "" + Files.getLastModifiedTime(parentFolder).toMillis();
            Path newFolder = parentFolder.getParent().resolve(parentFolder.getFileName().toString() + "_" + timestamp);
            Files.move(parentFolder, newFolder);
        }

        this.reportRootPath = reportRootPath;
        Path outErrCaptureFile = reportRootPath.resolve("outerr.log");
        outErrCapture = new OutErrCapture(outErrCaptureFile);
        LOG.info("=== Output capture started ({}) ===", outErrCaptureFile);
        cluster = new Cluster(testName, clusterConfiguration);
    }

    private static String generateId()
    {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String className = stackTrace[3].getClassName();
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        return simpleClassName + "_" + stackTrace[3].getMethodName();
    }

    @Override
    public void close() throws Exception
    {
        try
        {
            cluster.close();
        }
        finally
        {
            outErrCapture.close();
        }
    }

    public Path getReportRootPath()
    {
        return reportRootPath;
    }

    public Cluster getCluster()
    {
        return cluster;
    }

    private static class OutErrCapture implements AutoCloseable
    {
        private final PrintStream oldOut;
        private final PrintStream oldErr;
        private final PrintStream ps;

        public OutErrCapture(Path captureFile) throws IOException
        {
            Files.createDirectories(captureFile.getParent());
            ps = new PrintStream(new MultiplexingOutputStream(new FileOutputStream(captureFile.toFile()), new IgnoreCloseOutputStream(System.out)));
            oldOut = System.out;
            oldErr = System.err;
            System.setOut(ps);
            System.setErr(ps);
        }

        @Override
        public void close() throws Exception
        {
            System.setOut(oldOut);
            System.setErr(oldErr);

            ps.close();
        }
    }

    private static class MultiplexingOutputStream extends OutputStream
    {
        private final List<OutputStream> delegates = new ArrayList<>();

        public MultiplexingOutputStream(OutputStream... outputStreams)
        {
            this.delegates.addAll(Arrays.asList(outputStreams));
        }

        @Override
        public void write(int b) throws IOException
        {
            for (OutputStream outputStream : delegates)
            {
                outputStream.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException
        {
            for (OutputStream outputStream : delegates)
            {
                outputStream.write(b, off, len);
            }
        }

        @Override
        public void flush() throws IOException
        {
            for (OutputStream outputStream : delegates)
            {
                outputStream.flush();
            }
        }

        @Override
        public void close() throws IOException
        {
            for (OutputStream outputStream : delegates)
            {
                outputStream.close();
            }
        }
    }

    private static class IgnoreCloseOutputStream extends OutputStream
    {
        private final OutputStream delegate;

        public IgnoreCloseOutputStream(OutputStream outputStream)
        {
            this.delegate = outputStream;
        }

        @Override
        public void write(int b) throws IOException
        {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException
        {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException
        {
            delegate.flush();
        }

        @Override
        public void close() throws IOException
        {
        }
    }
}
