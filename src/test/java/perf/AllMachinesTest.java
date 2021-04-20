package perf;

import org.junit.jupiter.api.Test;
import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.NodeArray;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
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
            .hostLauncher(new SshRemoteHostLauncher("ubuntu"))
            .nodeArray(new SimpleNodeArrayConfiguration("server-array").topology(new NodeArrayTopology(
                new Node("1", "load-master"),
                new Node("2", "load-1"),
                new Node("3", "load-2"),
                new Node("4", "load-3"),
                new Node("5", "load-4")
            )));

        try (Cluster cluster = new Cluster(cfg))
        {
            NodeArray nodeArray = cluster.nodeArray("server-array");

            nodeArray.executeOnAll(tools ->
            {
                String javaVersion = System.getProperty("java.version");
                System.out.println("hello, world! from java " + javaVersion);
            }).get();
        }
    }
}
