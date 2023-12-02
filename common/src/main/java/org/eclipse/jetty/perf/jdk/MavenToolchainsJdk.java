package org.eclipse.jetty.perf.jdk;

import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.mortbay.jetty.orchestrator.util.FilenameSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class MavenToolchainsJdk implements FilenameSupplier
{
    private static final Logger LOG = LoggerFactory.getLogger(MavenToolchainsJdk.class);

    private final String version;

    public MavenToolchainsJdk(String version)
    {
        this.version = version;
    }

    @Override
    public String get(FileSystem fileSystem, String hostname)
    {
        try
        {
            String jdkHome = findJavaHomeFromToolchain(fileSystem, hostname);
            if (jdkHome != null)
            {
                LOG.debug("host '{}' found jdkHome '{}' from toolchain", hostname, jdkHome);
                Path javaExec = Paths.get(jdkHome).resolve("bin").resolve("java"); // *nix
                if (!Files.isExecutable(javaExec))
                    javaExec = Paths.get(jdkHome).resolve("Contents").resolve("Home").resolve("bin").resolve("java"); // OSX
                if (!Files.isExecutable(javaExec))
                    javaExec = Paths.get(jdkHome).resolve("bin").resolve("java.exe"); // Windows
                if (Files.isExecutable(javaExec))
                {
                    // it's coming from toolchains so we trust the result
                    String absolutePath = javaExec.toAbsolutePath().toString();
                    if (LOG.isDebugEnabled())
                        LOG.debug("host '{}' will use java executable {}", hostname, absolutePath);
                    return absolutePath;
                }
            }
            throw new RuntimeException("Toolchains JDK '" + version + "' not found for host " + hostname);
        }
        catch (Exception x)
        {
            throw new RuntimeException("Error looking for toolchains JDK '" + version + "' for host " + hostname, x);
        }
    }

    protected String findJavaHomeFromToolchain(FileSystem fileSystem, String hostname) throws Exception
    {
        String fileName = hostname + "-toolchains.xml";
        Path toolchainsPath = fileSystem.getPath(fileName);
        if (!Files.exists(toolchainsPath))
        {
            LOG.debug("cannot find generated toolchains file {}", toolchainsPath);
            toolchainsPath = fileSystem.getPath(System.getProperty("user.home"), ".m2", "toolchains.xml");
        }

        if (Files.exists(toolchainsPath))
        {
            try (InputStream is = Files.newInputStream(toolchainsPath))
            {
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document xmlDocument = builder.parse(is);
                XPath xPath = XPathFactory.newInstance().newXPath();
                NodeList nodeList = (NodeList)xPath.compile("/toolchains/toolchain").evaluate(xmlDocument, XPathConstants.NODESET);
                for (int i = 0; i < nodeList.getLength(); i++)
                {
                    Node node = nodeList.item(i);
                    String version = (String)xPath.compile("provides/version").evaluate(node, XPathConstants.STRING);
                    if (version.equals(this.version))
                        return (String)xPath.compile("configuration/jdkHome").evaluate(node, XPathConstants.STRING);
                }
                return null;
            }
        }
        else
        {
            LOG.debug("cannot find configured toolchains file {}", toolchainsPath);
            return null;
        }
    }
}
