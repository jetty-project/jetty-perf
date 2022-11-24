package org.eclipse.jetty.perf.jdk;

import java.nio.file.FileSystem;
import java.util.Arrays;
import java.util.List;

import org.mortbay.jetty.orchestrator.util.FilenameSupplier;

public class LocalJdk implements FilenameSupplier
{
    private final List<FilenameSupplier> suppliers;
    private final String jdkName;

    public LocalJdk(String jdkName)
    {
        suppliers = Arrays.asList(new MavenToolchainsJdk(jdkName), new JenkinsToolJdk(jdkName));
        this.jdkName = jdkName;
    }

    @Override
    public String get(FileSystem fileSystem, String hostname)
    {
        for (FilenameSupplier supplier : suppliers)
        {
            try
            {
                return supplier.get(fileSystem, hostname);
            }
            catch (Exception e)
            {
                // ignore
            }
        }
        throw new RuntimeException("JDK '" + jdkName + "' not found for host " + hostname);
    }
}
