package org.eclipse.jetty.perf.monitoring.sjk;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.perf.monitoring.Monitor;
import org.eclipse.jetty.perf.util.IOUtil;
import org.eclipse.jetty.perf.util.JvmUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractSjkMonitor implements Monitor
{
    private static final Logger LOG = LoggerFactory.getLogger(AbstractSjkMonitor.class);
    private static final String VERSION = "0.21";

    public static final int DEFAULT_INTERVAL = 5;

    protected final Process process;

    protected AbstractSjkMonitor(String outputfilename, String... commandLine)
    {
        Process process = null;
        try
        {
            installIfNeeded();
            List<String> command = buildCommand(commandLine);

            File outputFile = new File(outputfilename);
            if (!outputFile.getParentFile().isDirectory() && !outputFile.getParentFile().mkdirs())
                throw new IOException("Cannot create folder for output file " + outputFile.getAbsolutePath() + " of command " + command);
            process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
                .start();
        }
        catch (IOException e)
        {
            LOG.warn("Error starting sjk command: {}", e.getMessage());
        }
        this.process = process;
    }

    private static List<String> buildCommand(String[] commandLine)
    {
        List<String> result = new ArrayList<>();
        result.add(JvmUtil.findCurrentJavaExecutable().toAbsolutePath().toString());
        result.add("-jar");
        result.add(getSjkPath().toString());
        result.addAll(List.of(commandLine));
        return result;
    }

    private static void installIfNeeded() throws IOException
    {
        Path sjkPath = getSjkPath();
        Path parentPath = sjkPath.getParent();
        if (!Files.isRegularFile(sjkPath))
        {
            LOG.debug("installing sjk...");
            try
            {
                Files.createDirectories(parentPath);
            }
            catch (FileAlreadyExistsException e)
            {
                // this is fine
            }
            try (InputStream is = URI.create("https://repo1.maven.org/maven2/org/gridkit/jvmtool/sjk/" + VERSION + "/sjk-" + VERSION + ".jar").toURL().openStream();
                 OutputStream os = Files.newOutputStream(sjkPath))
            {
                IOUtil.copy(is, os);
            }
            LOG.debug("installed sjk");
        }
    }

    private static Path getSjkPath()
    {
        String home = System.getProperty("user.home") + "/downloads/sjk-" + VERSION + ".jar";
        return Paths.get(home);
    }

    @Override
    public void close() throws Exception
    {
        if (process != null)
        {
            process.destroy();
            process.waitFor();
        }
    }
}
