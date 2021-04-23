package perf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
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
            LOG.debug("installing async profiler...");
            File tarGzFile = new File("async-profiler-" + VERSION + "-linux-x64.tar.gz");
            try (InputStream is = new URL("https://github.com/jvm-profiling-tools/async-profiler/releases/download/v" + VERSION + "/async-profiler-" + VERSION + "-linux-x64.tar.gz").openStream();
                 OutputStream os = new FileOutputStream(tarGzFile))
            {
                IOUtil.copy(is, os);
            }
            TarGZipUnArchiver ua = new TarGZipUnArchiver(tarGzFile);
            ua.setDestDirectory(new File("."));
            ua.extract();
        }
    }

    private static void startAsyncProfiler(long pid) throws IOException, InterruptedException
    {
        LOG.debug("starting async profiler...");
        File asyncProfilerHome = new File("async-profiler-" + VERSION + "-linux-x64");
        //System.load(asyncProfilerHome.getAbsolutePath() + "/build/libAsyncProfiler.so");
        int rc = new ProcessBuilder("./profiler.sh", "start", Long.toString(pid))
            .directory(asyncProfilerHome)
            .redirectErrorStream(true)
            .start()
            .waitFor();
        if (rc != 0)
            LOG.warn("async profiler start failed with code " + rc);
        LOG.debug("started async profiler...");
    }

    private static void stopAsyncProfiler(String flamegraphFilename, long pid) throws IOException, InterruptedException
    {
        LOG.debug("stopping async profiler...");
        File asyncProfilerHome = new File("async-profiler-" + VERSION + "-linux-x64");
        File fgFile = new File(flamegraphFilename);
        int rc = new ProcessBuilder("./profiler.sh", "stop", "-f", fgFile.getAbsolutePath(), Long.toString(pid))
            .directory(asyncProfilerHome)
            .redirectErrorStream(true)
            .start()
            .waitFor();
        if (rc != 0)
            LOG.warn("async profiler stop failed with code " + rc);
        LOG.debug("stopped async profiler...");
    }
}
