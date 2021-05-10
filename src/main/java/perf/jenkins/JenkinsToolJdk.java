package perf.jenkins;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.toolchain.model.PersistedToolchains;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.apache.maven.toolchain.model.io.xpp3.MavenToolchainsXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.mortbay.jetty.orchestrator.util.FilenameSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        try
        {
            String jdkHome = findJavaHomeFromToolchain(hostname);
            if (StringUtils.isNotEmpty(jdkHome))
            {
                Path javaExec = Paths.get(jdkHome).resolve("bin/java");
                if (Files.isExecutable(javaExec))
                {
                    LOG.info("host {} will use java executable {}", hostname, javaExec.toAbsolutePath());
                    return javaExec.toAbsolutePath().toString();
                }
            }
            else
            {
                LOG.warn("cannot find jdkHome from toolchain for host {}", hostname);
            }
        }
        catch (IOException x)
        {
            LOG.debug("ignore error searching from toolchains file", x);
        }
        Path jdkFolderFile = fileSystem.getPath(System.getProperty("user.home"), "jenkins_home/tools/hudson.model.JDK", toolName);
        try
        {
            String executable = Files.walk(jdkFolderFile, 2)
                .filter(path -> Files.isExecutable(path.resolve("bin/java")))
                .map(path -> path.resolve("bin/java").toAbsolutePath().toString())
                .findAny()
                .orElseThrow(() -> new RuntimeException("Jenkins tool '" + toolName + "' not found"));
            if (LOG.isDebugEnabled())
                LOG.debug("Found java executable in Jenkins Tools '{}' of machine '{}' at {}", toolName, hostname, executable);
            LOG.info("host {} will use java executable {}", hostname, executable);
            return executable;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Jenkins tool '" + toolName + "' not found", e);
        }
    }

    protected String findJavaHomeFromToolchain(String hostname) throws IOException
    {
        String fileName = hostname + "-toolchains.xml";
        Path toolchainsPath = Paths.get(fileName);
        if (Files.exists(toolchainsPath))
        {
            MavenToolchainsXpp3Reader toolChainsReader = new MavenToolchainsXpp3Reader();
            try (InputStream inputStream = Files.newInputStream(toolchainsPath))
            {

                 PersistedToolchains toolchains = toolChainsReader.read(inputStream);
                 return (String) toolchains.getToolchains().stream().filter(o ->
                                                            {
                     ToolchainModel toolchainModel = ((ToolchainModel)o);
                     if ("jdk".equals(toolchainModel.getType()))
                     {
                        Xpp3Dom provides = (Xpp3Dom)toolchainModel.getProvides();
                        if (provides != null)
                        {
                            Xpp3Dom version = provides.getChild("version");
                            if (version != null && StringUtils.equals(version.getValue(), this.toolName))
                            {
                               return true;
                            }
                        }
                     }
                     return false;
                 }).map( o -> {
                     ToolchainModel toolchainModel = ((ToolchainModel)o);
                     Xpp3Dom configuration = (Xpp3Dom)toolchainModel.getConfiguration();
                     Xpp3Dom jdkHome = configuration.getChild("jdkHome");
                     return (jdkHome != null?jdkHome.getValue():null);
                 }).findFirst().orElse(null);
            }
            catch (XmlPullParserException x)
            {
                throw new IOException(x);
            }
        }
        else
        {
            LOG.info("cannot find toolchain file {}", fileName);
        }
        return null;
    }

}
