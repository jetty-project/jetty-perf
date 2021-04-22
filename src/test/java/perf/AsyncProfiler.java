package perf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.logging.AbstractLogger;
import org.codehaus.plexus.logging.Logger;
import org.mortbay.jetty.orchestrator.util.IOUtil;

public class AsyncProfiler implements AutoCloseable
{
    private Process process;

    public AsyncProfiler(String fgFilename, long pid) throws IOException
    {
        installAsyncProfilerIfNeeded();
        process = spawnAsyncProfiler(fgFilename, pid);
    }

    @Override
    public void close() throws Exception
    {
        if (process != null)
        {
            process.destroy();
            process.waitFor();
        }
        process = null;
    }

    private static void installAsyncProfilerIfNeeded() throws IOException
    {
        File asyncProfilerHome = new File("async-profiler-2.0-linux-x64");
        if (!asyncProfilerHome.isDirectory())
        {
            System.out.println("installing async profiler...");
            File tarGzFile = new File("async-profiler-2.0-linux-x64.tar.gz");
            try (InputStream is = new URL("https://github.com/jvm-profiling-tools/async-profiler/releases/download/v2.0/async-profiler-2.0-linux-x64.tar.gz").openStream();
                 OutputStream os = new FileOutputStream(tarGzFile))
            {
                IOUtil.copy(is, os);
            }
            TarGZipUnArchiver ua = new TarGZipUnArchiver(tarGzFile);
            ua.enableLogging(new AbstractLogger(0, "") {
                @Override
                public void debug(String s, Throwable throwable)
                {

                }

                @Override
                public void info(String s, Throwable throwable)
                {

                }

                @Override
                public void warn(String s, Throwable throwable)
                {

                }

                @Override
                public void error(String s, Throwable throwable)
                {

                }

                @Override
                public void fatalError(String s, Throwable throwable)
                {

                }

                @Override
                public Logger getChildLogger(String s)
                {
                    return null;
                }
            });
            ua.setDestDirectory(tarGzFile.getParentFile());
            ua.extract();
        }
    }

    private static Process spawnAsyncProfiler(String fgFilename, long pid) throws IOException
    {
        File asyncProfilerHome = new File("async-profiler-2.0-linux-x64");
        File fgFile = new File(fgFilename);
        return new ProcessBuilder("./profiler.sh", "-d", "3600", "-f", fgFile.getAbsolutePath(), Long.toString(pid))
            .directory(asyncProfilerHome)
            .redirectErrorStream(true)
            .start();
    }
}
