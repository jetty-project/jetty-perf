package org.eclipse.jetty.perf.monitoring.asyncprof;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.eclipse.jetty.perf.monitoring.Monitor;
import org.eclipse.jetty.perf.util.IOUtil;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractAsyncProfilerMonitor implements Monitor
{
    private static final Logger LOG = LoggerFactory.getLogger(AbstractAsyncProfilerMonitor.class);
    private static final String VERSION = "2.9";

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
            LOG.debug("installing async profiler...");
            File tarGzFile = new File(asyncProfilerHome.getParentFile(), "async-profiler-" + VERSION + "-linux-x64.tar.gz");
            try (InputStream is = new URL("https://github.com/jvm-profiling-tools/async-profiler/releases/download/v" + VERSION + "/async-profiler-" + VERSION + "-linux-x64.tar.gz").openStream();
                 OutputStream os = new FileOutputStream(tarGzFile))
            {
                IOUtil.copy(is, os);
            }
            unTarGz(asyncProfilerHome, tarGzFile);
        }
    }

    private static void unTarGz(File targetFolder, File tarGzFile) throws IOException
    {
        try (TarInputStream tis = new TarInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(tarGzFile)))))
        {
            byte[] buffer = new byte[2048];
            while (true)
            {
                TarEntry entry = tis.getNextEntry();
                if (entry == null)
                    break;

                if (entry.isDirectory())
                {
                    File folder = new File(targetFolder.getParentFile(), entry.getName());
                    if (!folder.mkdirs())
                        throw new IOException("Cannot create folder: " + folder);
                    continue;
                }

                File file = new File(targetFolder.getParentFile(), entry.getName());
                try (BufferedOutputStream dest = new BufferedOutputStream(new FileOutputStream(file)))
                {
                    while (true)
                    {
                        int count = tis.read(buffer);
                        if (count == -1)
                            break;
                        dest.write(buffer, 0, count);
                    }
                }
                boolean executable = (entry.getHeader().mode & 0_111 /* Yes, octal. */) != 0;
                if (!file.setExecutable(executable))
                    throw new IOException("Cannot set executable: " + file);
            }
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
