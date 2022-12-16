package org.eclipse.jetty.perf.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutputCapturer implements AutoCloseable
{
    private static final Logger LOG = LoggerFactory.getLogger(OutputCapturer.class);

    private final OutErrCapture outErrCapture;

    public OutputCapturer(Path reportRootPath) throws Exception
    {
        Path outErrCaptureFile = reportRootPath.resolve("outerr.log");
        outErrCapture = new OutErrCapture(outErrCaptureFile);
        LOG.info("=== Output capture started ({}) ===", outErrCaptureFile);
    }

    @Override
    public void close()
    {
        outErrCapture.close();
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
        public void close()
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
        public void close()
        {
        }
    }
}
