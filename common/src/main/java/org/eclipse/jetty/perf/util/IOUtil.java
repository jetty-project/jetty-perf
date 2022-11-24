package org.eclipse.jetty.perf.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IOUtil
{
    private static final Logger LOG = LoggerFactory.getLogger(IOUtil.class);

    public static void close(AutoCloseable closeable)
    {
        try
        {
            if (closeable != null)
                closeable.close();
        }
        catch (Exception e)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("", e);
        }
    }

    public static void copy(InputStream is, OutputStream os) throws IOException
    {
        copy(is, os, 1024, false);
    }

    public static void copy(InputStream is, OutputStream os, int bufferSize, boolean flushOnWrite) throws IOException
    {
        byte[] buffer = new byte[bufferSize];
        while (true)
        {
            int read = is.read(buffer);
            if (read == -1)
                return;
            os.write(buffer, 0, read);
            if (flushOnWrite)
                os.flush();
        }
    }
}
