package perf;

import java.nio.file.FileSystem;

import org.junit.jupiter.api.Test;
import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.NodeArray;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.Jvm;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.SimpleClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleNodeArrayConfiguration;
import org.mortbay.jetty.orchestrator.util.FilenameSupplier;
import perf.jenkins.JenkinsToolJdk;

public class AllMachinesTest
{
    @Test
    public void testAllMachines() throws Exception
    {
        String jdkName = System.getProperty("test.jdk.name", "load-jdk11");
        ClusterConfiguration cfg = new SimpleClusterConfiguration()
            .jvm(new Jvm(new JenkinsToolOnLinuxDefaultOnWindows(jdkName)))
            .nodeArray(new SimpleNodeArrayConfiguration("all-machines")
                .node(new Node("01", "load-master"))
                .node(new Node("02", "load-1"))
                .node(new Node("03", "load-2"))
                .node(new Node("04", "load-3"))
                .node(new Node("05", "load-4"))
                .node(new Node("06", "load-5"))
                .node(new Node("07", "load-6"))
                .node(new Node("08", "load-7"))
                .node(new Node("09", "load-8"))
                .node(new Node("10", "load-sample"))
                .node(new Node("11", "zwerg"))
                .node(new Node("12", "ci-windows"))
            );

        {
            String javaVersion = System.getProperty("java.version");
            String username = System.getProperty("user.name");
            System.out.println("jenkins running java " + javaVersion + " with user " + username);
        }

        try (Cluster cluster = new Cluster(cfg))
        {
            NodeArray allMachinesArray = cluster.nodeArray("all-machines");

            allMachinesArray.executeOnAll(tools ->
            {
                String javaVersion = System.getProperty("java.version");
                String username = System.getProperty("user.name");
                String osName = System.getProperty("os.name");
                String osArch = System.getProperty("os.arch");
                System.out.println("Running java '" + javaVersion + "' with user '" + username + "' on OS '" + osName + "' on arch '" + osArch + "'");
            }).get();
        }
    }

    private static class JenkinsToolOnLinuxDefaultOnWindows implements FilenameSupplier
    {
        private final JenkinsToolJdk jenkinsToolJdk;

        public JenkinsToolOnLinuxDefaultOnWindows(String jdkName)
        {
            this.jenkinsToolJdk = new JenkinsToolJdk(jdkName);
        }

        @Override
        public String get(FileSystem fileSystem, String hostname)
        {
            if (hostname.contains("windows"))
                return "java";
            return jenkinsToolJdk.get(fileSystem, hostname);
        }
    }
}
