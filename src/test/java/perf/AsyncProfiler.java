package perf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.logging.AbstractLogger;
import org.mortbay.jetty.orchestrator.util.IOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncProfiler implements AutoCloseable
{
    private static final Logger LOG = LoggerFactory.getLogger(AsyncProfiler.class);
    public static final String VERSION = "2.0";

    private final String flamegraphFilename;
    private final long pid;

    public AsyncProfiler(String flamegraphFilename, long pid) throws Exception
    {
        this.flamegraphFilename = flamegraphFilename;
        this.pid = pid;
        installAsyncProfilerIfNeeded();
        startAsyncProfiler(pid);
    }

    @Override
    public void close() throws Exception
    {
        stopAsyncProfiler(flamegraphFilename, pid);
    }

    private static void installAsyncProfilerIfNeeded() throws IOException
    {
        File asyncProfilerHome = new File("async-profiler-" + VERSION + "-linux-x64");
        if (!asyncProfilerHome.isDirectory())
        {
            LOG.info("installing async profiler...");
            File tarGzFile = new File("async-profiler-" + VERSION + "-linux-x64.tar.gz");
            try (InputStream is = new URL("https://github.com/jvm-profiling-tools/async-profiler/releases/download/v" + VERSION + "/async-profiler-" + VERSION + "-linux-x64.tar.gz").openStream();
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
                public org.codehaus.plexus.logging.Logger getChildLogger(String s)
                {
                    return null;
                }
            });
            ua.setDestDirectory(new File("."));
            ua.extract();
        }
    }

    private static void startAsyncProfiler(long pid) throws IOException, InterruptedException
    {
        LOG.info("starting async profiler...");
        File asyncProfilerHome = new File("async-profiler-" + VERSION + "-linux-x64");
        //System.load(asyncProfilerHome.getAbsolutePath() + "/build/libAsyncProfiler.so");
        new ProcessBuilder("./profiler.sh", "start", Long.toString(pid))
            .directory(asyncProfilerHome)
            .redirectErrorStream(true)
            .start()
            .waitFor();
        LOG.info("started async profiler...");
    }

    private static void stopAsyncProfiler(String flamegraphFilename, long pid) throws IOException, InterruptedException
    {
        LOG.info("stopping async profiler...");
        File asyncProfilerHome = new File("async-profiler-" + VERSION + "-linux-x64");
        File fgFile = new File(flamegraphFilename);
        new ProcessBuilder("./profiler.sh", "stop", "-f", fgFile.getAbsolutePath(), Long.toString(pid))
            .directory(asyncProfilerHome)
            .redirectErrorStream(true)
            .start()
            .waitFor();
        LOG.info("stopped async profiler...");
    }
}
