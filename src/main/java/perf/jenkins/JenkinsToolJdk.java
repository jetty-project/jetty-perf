package perf.jenkins;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.mortbay.jetty.orchestrator.util.FilenameSupplier;

public class JenkinsToolJdk implements FilenameSupplier
{
    private final String toolName;

    public JenkinsToolJdk(String toolName)
    {
        this.toolName = toolName;
    }

    @Override
    public String get(FileSystem fileSystem, String hostname)
    {
        Path jdkFolderFile = fileSystem.getPath("jenkins_home/tools/hudson.model.JDK/" + toolName);
        try
        {
            return Files.walk(jdkFolderFile, 2)
                .filter(path -> Files.isExecutable(path.resolve("bin/java")))
                .map(path -> path.resolve("bin/java").toAbsolutePath().toString())
                .findAny()
                .orElseThrow(() -> new RuntimeException("Jenkins tool '" + toolName + "' not found"));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Jenkins tool '" + toolName + "' not found", e);
        }
    }
}
