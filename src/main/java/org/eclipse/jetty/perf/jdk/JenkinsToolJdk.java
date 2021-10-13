package org.eclipse.jetty.perf.jdk;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.mortbay.jetty.orchestrator.util.FilenameSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JenkinsToolJdk implements FilenameSupplier
{
    private static final Logger LOG = LoggerFactory.getLogger(JenkinsToolJdk.class);

    private final String toolName;

    public JenkinsToolJdk(String toolName)
    {
        this.toolName = toolName;
    }

    @Override
    public String get(FileSystem fileSystem, String hostname)
    {
        Path jdkFolderFile = fileSystem.getPath("jenkins_home", "tools", "hudson.model.JDK", toolName);
        if (!Files.isDirectory(jdkFolderFile))
            jdkFolderFile = fileSystem.getPath("tools", "hudson.model.JDK", toolName);
        if (!Files.isDirectory(jdkFolderFile))
            throw new RuntimeException("Jenkins tool '" + toolName + "' not found in " + jdkFolderFile.toAbsolutePath());
        try
        {
            String executable = Files.walk(jdkFolderFile, 2)
                .filter(path ->
                    Files.isExecutable(path.resolve("bin").resolve("java")) || Files.isExecutable(path.resolve("bin").resolve("java.exe")))
                .map(path ->
                {
                    Path resolved = path.resolve("bin").resolve("java");
                    if (!Files.isExecutable(resolved))
                        resolved = path.resolve("bin").resolve("java.exe");
                    return resolved.toAbsolutePath().toString();
                })
                .findAny()
                .orElseThrow(() -> new RuntimeException("Jenkins tool '" + toolName + "' not found"));
            if (LOG.isDebugEnabled())
                LOG.debug("Found java executable in Jenkins Tools '{}' of machine '{}' at {}", toolName, hostname, executable);
            LOG.debug("host {} will use java executable {}", hostname, executable);
            return executable;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Jenkins tool '" + toolName + "' not found", e);
        }
    }
}
