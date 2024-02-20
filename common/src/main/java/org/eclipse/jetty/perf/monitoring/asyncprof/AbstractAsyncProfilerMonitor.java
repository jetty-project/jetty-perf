package org.eclipse.jetty.perf.monitoring.asyncprof;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
    private static final String VERSION = "3.0";

    private final long pid;
    private final Path outputPath;

    protected AbstractAsyncProfilerMonitor(String outputFilename) throws Exception
    {
        this(ProcessHandle.current().pid(), outputFilename);
    }

    protected AbstractAsyncProfilerMonitor(long pid, String outputFilename) throws Exception
    {
        this.pid = pid;
        this.outputPath = Path.of(outputFilename);
        try
        {
            Files.createDirectories(outputPath.getParent());
        }
        catch (FileAlreadyExistsException e)
        {
            // this is fine
        }
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
        Path asyncProfilerHome = getAsyncProfilerHome();
        if (!Files.isDirectory(asyncProfilerHome))
        {
            LOG.debug("installing async profiler...");
            Path parentFolder = asyncProfilerHome.getParent();
            try
            {
                Files.createDirectories(parentFolder);
            }
            catch (FileAlreadyExistsException e)
            {
                // this is fine
            }
            Path tarGzFile = parentFolder.resolve("async-profiler-" + VERSION + "-linux-x64.tar.gz");
            try (InputStream is = URI.create("https://github.com/jvm-profiling-tools/async-profiler/releases/download/v" + VERSION + "/async-profiler-" + VERSION + "-linux-x64.tar.gz").toURL().openStream();
                 OutputStream os = Files.newOutputStream(tarGzFile))
            {
                IOUtil.copy(is, os);
            }
            unTarGz(asyncProfilerHome, tarGzFile);
        }
    }

    private static void unTarGz(Path targetFolder, Path tarGzFile) throws IOException
    {
        try (TarInputStream tis = new TarInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(tarGzFile)))))
        {
            while (true)
            {
                TarEntry entry = tis.getNextEntry();
                if (entry == null)
                    break;

                if (entry.isDirectory())
                {
                    Path folder = targetFolder.getParent().resolve(entry.getName());
                    Files.createDirectories(folder);
                    continue;
                }

                Path file = targetFolder.getParent().resolve(entry.getName());
                try (BufferedOutputStream dest = new BufferedOutputStream(Files.newOutputStream(file)))
                {
                    IOUtil.copy(tis, dest);
                }
                boolean executable = (entry.getHeader().mode & 0_111 /* Yes, octal. */) != 0;
                if (!file.toFile().setExecutable(executable))
                    throw new IOException("Cannot set executable: " + file);
            }
        }
    }

    private static Path getAsyncProfilerHome()
    {
        String home = System.getProperty("user.home") + "/downloads/async-profiler-" + VERSION + "-linux-x64";
        return Paths.get(home);
    }

    private void startAsyncProfiler(long pid) throws IOException, InterruptedException
    {
        LOG.debug("starting async profiler...");
        Path asyncProfilerHome = getAsyncProfilerHome();

        List<String> cmdLine = new ArrayList<>();
        cmdLine.add(getProfilerCmd(asyncProfilerHome));
        cmdLine.add("start");
        cmdLine.addAll(extraStartCmdLineArgs());
        cmdLine.add(Long.toString(pid));

        int rc = new ProcessBuilder(cmdLine.toArray(new String[0]))
            .directory(asyncProfilerHome.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor();
        if (rc != 0)
            LOG.warn("async profiler start failed with code " + rc);
        LOG.debug("started async profiler...");
    }

    private static String getProfilerCmd(Path asyncProfilerHome)
    {
        return Files.isExecutable(asyncProfilerHome.resolve("bin").resolve("asprof")) ? "./bin/asprof" : "./profiler.sh";
    }

    protected abstract Collection<String> extraStartCmdLineArgs();

    private void stopAsyncProfiler(long pid) throws IOException, InterruptedException
    {
        LOG.debug("stopping async profiler...");
        Path asyncProfilerHome = getAsyncProfilerHome();

        List<String> cmdLine = new ArrayList<>();
        cmdLine.add(getProfilerCmd(asyncProfilerHome));
        cmdLine.add("stop");
        cmdLine.add("-f");
        cmdLine.add(outputPath.toAbsolutePath().toString());
        cmdLine.add(Long.toString(pid));

        int rc = new ProcessBuilder(cmdLine.toArray(new String[0]))
            .directory(asyncProfilerHome.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor();
        if (rc != 0)
            LOG.warn("async profiler stop failed with code " + rc);
        LOG.debug("stopped async profiler...");
    }
}
