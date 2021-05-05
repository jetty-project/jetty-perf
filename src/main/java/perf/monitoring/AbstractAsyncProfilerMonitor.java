package perf.monitoring;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.logging.slf4j.Slf4jLogger;
import perf.util.IOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractAsyncProfilerMonitor implements Monitor
{
    private static final Logger LOG = LoggerFactory.getLogger(AbstractAsyncProfilerMonitor.class);
    private static final String VERSION = "2.0";

    private final long pid;

    protected AbstractAsyncProfilerMonitor() throws Exception
    {
        this(ProcessHandle.current().pid());
    }

    protected AbstractAsyncProfilerMonitor(long pid) throws Exception
    {
        this.pid = pid;
        installAsyncProfilerIfNeeded();
        startAsyncProfiler(pid);
    }

    @Override
    public void close() throws Exception
    {
        stopAsyncProfiler(pid);
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

    private void startAsyncProfiler(long pid) throws IOException, InterruptedException
    {
        LOG.debug("starting async profiler...");
        File asyncProfilerHome = getAsyncProfilerHome();

        List<String> cmdLine = new ArrayList<>(Arrays.asList("./profiler.sh", "start"));
        cmdLine.addAll(extraStartCmdLineArgs());
        cmdLine.add(Long.toString(pid));

        int rc = new ProcessBuilder(cmdLine.toArray(new String[0]))
            .directory(asyncProfilerHome)
            .redirectErrorStream(true)
            .start()
            .waitFor();
        if (rc != 0)
            LOG.warn("async profiler start failed with code " + rc);
        LOG.debug("started async profiler...");
    }

    protected abstract Collection<String> extraStartCmdLineArgs();
    protected abstract Collection<String> extraStopCmdLineArgs();

    private void stopAsyncProfiler(long pid) throws IOException, InterruptedException
    {
        LOG.debug("stopping async profiler...");
        File asyncProfilerHome = getAsyncProfilerHome();

        List<String> cmdLine = new ArrayList<>(Arrays.asList("./profiler.sh", "stop"));
        cmdLine.addAll(extraStopCmdLineArgs());
        cmdLine.add(Long.toString(pid));

        int rc = new ProcessBuilder(cmdLine.toArray(new String[0]))
            .directory(asyncProfilerHome)
            .redirectErrorStream(true)
            .start()
            .waitFor();
        if (rc != 0)
            LOG.warn("async profiler stop failed with code " + rc);
        LOG.debug("stopped async profiler...");
    }
}
