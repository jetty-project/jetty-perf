package org.eclipse.jetty.perf.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutputCapturingCluster implements AutoCloseable
{
    private static final Logger LOG = LoggerFactory.getLogger(OutputCapturingCluster.class);

    private final OutErrCapture outErrCapture;
    private final Cluster cluster;

    public OutputCapturingCluster(ClusterConfiguration clusterConfiguration, Path outErrCaptureFile) throws Exception
    {
        outErrCapture = new OutErrCapture(outErrCaptureFile);
        LOG.info("=== Output capture started ===");
        cluster = new Cluster(generateId(), clusterConfiguration);
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
            this(captureFile.toFile());
        }

        private OutErrCapture(File captureFile) throws IOException
        {
            if (!captureFile.getParentFile().mkdirs())
                throw new IOException("Cannot create folder for out/err capture file");
            ps = new PrintStream(new FileOutputStream(captureFile));

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
}
