package perf.jenkins;

import java.io.File;

import org.mortbay.jetty.orchestrator.util.SerializableSupplier;

public class JenkinsToolJdk implements SerializableSupplier<String>
{
    private final String toolName;

    public JenkinsToolJdk(String toolName)
    {
        this.toolName = toolName;
    }

    @Override
    public String get()
    {
        String home = System.getProperty("user.home");
        File jdkFolderFile = new File(home + "/jenkins_home/tools/hudson.model.JDK/" + toolName);
        if (!jdkFolderFile.isDirectory())
            throw new RuntimeException("Jenkins tool '" + toolName + "' not installed");
        File executableFile = new File(jdkFolderFile, "bin/java");
        if (executableFile.isFile())
            return executableFile.getAbsolutePath();
        File[] files = jdkFolderFile.listFiles((dir, name) -> !name.startsWith(".timestamp"));
        if (files == null || files.length == 0)
            throw new RuntimeException("Jenkins tool '" + toolName + "' not found");
        for (File file : files)
        {
            executableFile = new File(file, "bin/java");
            if (executableFile.isFile())
                return executableFile.getAbsolutePath();
        }
        throw new RuntimeException("Jenkins tool '" + toolName + "' not found");
    }
}
