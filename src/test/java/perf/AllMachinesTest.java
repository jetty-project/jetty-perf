package perf;

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

public class AllMachinesTest
{
    @Test
    public void testAllMachines() throws Exception
    {
        ClusterConfiguration cfg = new SimpleClusterConfiguration()
            .jvm(new Jvm(()->
            {
                String javaHome = System.getenv("JAVA_HOME");
                if (javaHome != null)
                    return javaHome + "/bin/java";
                return "/home/ubuntu/programs/openjdk-12.0.0/bin/java";
            }))
            .hostLauncher(new SshRemoteHostLauncher("ubuntu"))
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

        try (Cluster cluster = new Cluster(cfg))
        {
            NodeArray serverArray = cluster.nodeArray("server");
            NodeArray loadersArray = cluster.nodeArray("loaders");
            NodeArray probeArray = cluster.nodeArray("probe");

            NodeArrayFuture serverFuture = serverArray.executeOnAll(tools ->
            {
                String javaVersion = System.getProperty("java.version");
                System.out.println("server running java " + javaVersion);
            });
            NodeArrayFuture loadersFuture = loadersArray.executeOnAll(tools ->
            {
                String javaVersion = System.getProperty("java.version");
                System.out.println("loaders running java " + javaVersion);
            });
            NodeArrayFuture probeFuture = probeArray.executeOnAll(tools ->
            {
                String javaVersion = System.getProperty("java.version");
                System.out.println("probe running java " + javaVersion);
            });

            serverFuture.get();
            loadersFuture.get();
            probeFuture.get();
        }
    }
}
