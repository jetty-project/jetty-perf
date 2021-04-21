//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.mortbay.jetty.orchestrator;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.HostLauncher;
import org.mortbay.jetty.orchestrator.configuration.LocalHostLauncher;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;
import org.mortbay.jetty.orchestrator.rpc.NodeProcess;
import org.mortbay.jetty.orchestrator.rpc.RpcClient;
import org.mortbay.jetty.orchestrator.rpc.command.KillNodeCommand;
import org.mortbay.jetty.orchestrator.rpc.command.SpawnNodeCommand;
import org.mortbay.jetty.orchestrator.util.IOUtil;

public class Cluster implements AutoCloseable
{
    private final String id;
    private final ClusterConfiguration configuration;
    private final LocalHostLauncher localHostLauncher = new LocalHostLauncher();
    private final HostLauncher hostLauncher;
    private final Map<String, NodeArray> nodeArrays = new HashMap<>(); // keyed by NodeId
    private final Map<String, Host> hosts = new HashMap<>(); // keyed by HostId
    private TestingServer zkServer;
    private CuratorFramework curator;

    public Cluster(ClusterConfiguration configuration) throws Exception
    {
        this(generateId(), configuration);
    }

    private static String generateId()
    {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String className = stackTrace[3].getClassName();
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        return sanitize(simpleClassName + "::" + stackTrace[3].getMethodName());
    }

    public Cluster(String id, ClusterConfiguration configuration) throws Exception
    {
        this.id = sanitize(id);
        this.configuration = configuration;
        this.hostLauncher = configuration.hostLauncher();

        try
        {
            init();
        }
        catch (Exception e)
        {
            close();
            throw e;
        }
    }

    private void init() throws Exception
    {
        zkServer = new TestingServer(true);
        String connectString = "localhost:" + zkServer.getPort();
        curator = CuratorFrameworkFactory.newClient(connectString, new RetryNTimes(0, 0));
        curator.start();
        curator.blockUntilConnected();

        Map<String, Map.Entry<String, RpcClient>> hostClients = new HashMap<>();
        List<String> hostnames = configuration.nodeArrays().stream()
            .flatMap(cfg -> cfg.topology().nodes().stream())
            .map(Node::getHostname)
            .distinct()
            .collect(Collectors.toList());
        for (String hostname : hostnames)
        {
            String hostId = hostIdFor(hostname);
            HostLauncher launcher = hostname.equals(LocalHostLauncher.HOSTNAME) ? localHostLauncher : hostLauncher;
            if (launcher == null)
                throw new IllegalStateException("No configured host launcher to start node on " + hostname);
            String remoteConnectString = launcher.launch(hostname, hostId, connectString);
            hostClients.put(hostId, new AbstractMap.SimpleImmutableEntry<>(remoteConnectString, new RpcClient(curator, hostId)));
        }

        for (NodeArrayConfiguration nodeArrayConfiguration : configuration.nodeArrays())
        {
            Map<String, NodeArray.Node> nodes = new HashMap<>();
            for (Node node : nodeArrayConfiguration.topology().nodes())
            {
                String hostname = node.getHostname();
                boolean localNode = hostname.equals(LocalHostLauncher.HOSTNAME);
                String hostId = hostIdFor(hostname);
                String nodeId = hostId + "/" + sanitize(nodeArrayConfiguration.id()) + "/" + sanitize(node.getId());
                nodes.put(node.getId(), new NodeArray.Node(nodeId, new RpcClient(curator, nodeId), localNode));

                Map.Entry<String, RpcClient> entry = hostClients.get(hostId);
                RpcClient rpcClient = entry.getValue();
                String remoteConnectString = entry.getKey();
                try
                {
                    NodeProcess remoteProcess = (NodeProcess)rpcClient.call(new SpawnNodeCommand(nodeArrayConfiguration.jvm(), hostId, nodeId, remoteConnectString));
                    hosts.compute(hostId, (key, host) ->
                    {
                        if (host == null)
                            host = new Host(rpcClient);
                        host.nodeProcesses.add(remoteProcess);
                        return host;
                    });
                }
                catch (Exception e)
                {
                    throw new Exception("Error spawning node '" + nodeId + "'", e);
                }
            }
            nodeArrays.put(nodeArrayConfiguration.id(), new NodeArray(nodes));
        }
    }

    private static String sanitize(String id)
    {
        return id.replace(':', '_')
            .replace('/', '_');
    }

    private String hostIdFor(String hostname)
    {
        return id + "/" + sanitize(hostname);
    }

    public ClusterTools tools()
    {
        return new ClusterTools(curator, hostIdFor(LocalHostLauncher.HOSTNAME));
    }

    @Override
    public void close()
    {
        hosts.values().forEach(IOUtil::close);
        hosts.clear();
        nodeArrays.clear();
        IOUtil.close(hostLauncher);
        IOUtil.close(localHostLauncher);
        IOUtil.close(curator);
        IOUtil.close(zkServer);
    }

    public NodeArray nodeArray(String id)
    {
        return nodeArrays.get(id);
    }

    private static class Host implements AutoCloseable
    {
        private final RpcClient rpcClient;
        private final List<NodeProcess> nodeProcesses = new ArrayList<>();

        private Host(RpcClient rpcClient)
        {
            this.rpcClient = rpcClient;
        }

        @Override
        public void close()
        {
            for (NodeProcess nodeProcess : nodeProcesses)
            {
                try
                {
                    rpcClient.call(new KillNodeCommand(nodeProcess));
                }
                catch (Exception e)
                {
                    // ignore
                }
            }
            IOUtil.close(rpcClient);
        }
    }
}
