package perf;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.NodeArray;
import org.mortbay.jetty.orchestrator.NodeArrayFuture;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.Jvm;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayTopology;
import org.mortbay.jetty.orchestrator.configuration.SimpleClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SimpleNodeArrayConfiguration;
import org.mortbay.jetty.orchestrator.configuration.SshRemoteHostLauncher;
import org.mortbay.jetty.orchestrator.util.SerializableSupplier;

public class AllMachinesTest
{
    @Test
    public void testAllMachines() throws Exception
    {
        ClusterConfiguration cfg = new SimpleClusterConfiguration()
            .jvm(new Jvm(new JenkinsJdkTool("jdk11")))
            .hostLauncher(new SshRemoteHostLauncher())
            .nodeArray(new SimpleNodeArrayConfiguration("server").topology(new NodeArrayTopology(
                new Node("1", "load-master")
            )))
            .nodeArray(new SimpleNodeArrayConfiguration("loaders").topology(new NodeArrayTopology(
                new Node("1", "load-1"),
                new Node("2", "load-2"),
                new Node("3", "load-3")
            )))
            .nodeArray(new SimpleNodeArrayConfiguration("probe").topology(new NodeArrayTopology(
                new Node("1", "load-4")
            )))
            ;

        {
            String javaVersion = System.getProperty("java.version");
            String username = System.getProperty("user.name");
            System.out.println("jenkins running java " + javaVersion + " with user " + username);
        }

        try (Cluster cluster = new Cluster(cfg))
        {
            NodeArray serverArray = cluster.nodeArray("server");
            NodeArray loadersArray = cluster.nodeArray("loaders");
            NodeArray probeArray = cluster.nodeArray("probe");

            NodeArrayFuture serverFuture = serverArray.executeOnAll(tools ->
            {
                String javaVersion = System.getProperty("java.version");
                String username = System.getProperty("user.name");
                System.out.println("server running java " + javaVersion + " with user " + username);
            });
            NodeArrayFuture loadersFuture = loadersArray.executeOnAll(tools ->
            {
                String javaVersion = System.getProperty("java.version");
                String username = System.getProperty("user.name");
                System.out.println("loaders running java " + javaVersion + " with user " + username);
            });
            NodeArrayFuture probeFuture = probeArray.executeOnAll(tools ->
            {
                String javaVersion = System.getProperty("java.version");
                String username = System.getProperty("user.name");
                System.out.println("probe running java " + javaVersion + " with user " + username);
            });

            serverFuture.get();
            loadersFuture.get();
            probeFuture.get();
        }
    }

    private static class JenkinsJdkTool implements SerializableSupplier<String>
    {
        private final String toolName;

        private JenkinsJdkTool(String toolName)
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
            File[] files = jdkFolderFile.listFiles((dir, name) -> !name.startsWith(".timestamp"));
            if (files == null || files.length == 0)
                throw new RuntimeException("Jenkins tool '" + toolName + "' not found");
            File executableFile = new File(files[0], "bin/java");
            return executableFile.getAbsolutePath();
        }
    }
}
