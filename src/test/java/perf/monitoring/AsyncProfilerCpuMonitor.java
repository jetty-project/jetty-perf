package perf.monitoring;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.logging.slf4j.Slf4jLogger;
import org.mortbay.jetty.orchestrator.util.IOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AsyncProfilerCpuMonitor implements Monitor
{
    public static final String DEFAULT_FILENAME = "async-profiler-cpu.html";

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProfilerCpuMonitor.class);
    private static final String VERSION = "2.0";

    private final String outputFilename;
    private final long pid;

    public AsyncProfilerCpuMonitor() throws Exception
    {
        this(DEFAULT_FILENAME, ProcessHandle.current().pid());
    }

    public AsyncProfilerCpuMonitor(String outputFilename) throws Exception
    {
        this(outputFilename, ProcessHandle.current().pid());
    }

    public AsyncProfilerCpuMonitor(String outputFilename, long pid) throws Exception
    {
        this.outputFilename = outputFilename;
        this.pid = pid;
        installAsyncProfilerIfNeeded();
        startAsyncProfiler(pid);
    }

    @Override
    public void close() throws Exception
    {
        stopAsyncProfiler(outputFilename, pid);
    }

    private static void installAsyncProfilerIfNeeded() throws IOException
    {
        File asyncProfilerHome = getAsyncProfilerHome();
        if (!asyncProfilerHome.isDirectory())
        {
            if (!asyncProfilerHome.mkdirs())
                throw new IOException("Error creating async profiler home folder: " + asyncProfilerHome);
            LOG.debug("installing async profiler...");
            File tarGzFile = new File(asyncProfilerHome.getParentFile(), "async-profiler-" + VERSION + "-linux-x64.tar.gz");
            try (InputStream is = new URL("https://github.com/jvm-profiling-tools/async-profiler/releases/download/v" + VERSION + "/async-profiler-" + VERSION + "-linux-x64.tar.gz").openStream();
                 OutputStream os = new FileOutputStream(tarGzFile))
            {
                IOUtil.copy(is, os);
            }
            TarGZipUnArchiver ua = new TarGZipUnArchiver(tarGzFile);
            ua.enableLogging(new Slf4jLogger(org.codehaus.plexus.logging.Logger.LEVEL_INFO, LOG));
            ua.setDestDirectory(asyncProfilerHome.getParentFile());
            ua.extract();
        }
    }

    private static File getAsyncProfilerHome()
    {
        String home = System.getProperty("user.home") + "/downloads/async-profiler-" + VERSION + "-linux-x64";
        return new File(home);
    }

    private static void startAsyncProfiler(long pid) throws IOException, InterruptedException
    {
        LOG.debug("starting async profiler...");
        File asyncProfilerHome = getAsyncProfilerHome();
        int rc = new ProcessBuilder("./profiler.sh", "start", Long.toString(pid))
            .directory(asyncProfilerHome)
            .redirectErrorStream(true)
            .start()
            .waitFor();
        if (rc != 0)
            LOG.warn("async profiler start failed with code " + rc);
        LOG.debug("started async profiler...");
    }

    private static void stopAsyncProfiler(String outputFilename, long pid) throws IOException, InterruptedException
    {
        LOG.debug("stopping async profiler...");
        File asyncProfilerHome = getAsyncProfilerHome();
        File fgFile = new File(outputFilename);
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
