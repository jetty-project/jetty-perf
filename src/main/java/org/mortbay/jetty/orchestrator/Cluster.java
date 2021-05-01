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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
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
import org.mortbay.jetty.orchestrator.rpc.GlobalNodeId;
import org.mortbay.jetty.orchestrator.rpc.NodeProcess;
import org.mortbay.jetty.orchestrator.rpc.RpcClient;
import org.mortbay.jetty.orchestrator.rpc.command.CheckNodeCommand;
import org.mortbay.jetty.orchestrator.rpc.command.KillNodeCommand;
import org.mortbay.jetty.orchestrator.rpc.command.SpawnNodeCommand;
import org.mortbay.jetty.orchestrator.util.IOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cluster implements AutoCloseable
{
    private static final Logger LOG = LoggerFactory.getLogger(Cluster.class);

    private final String id;
    private final ClusterConfiguration configuration;
    private final LocalHostLauncher localHostLauncher = new LocalHostLauncher();
    private final HostLauncher hostLauncher;
    private final Map<String, NodeArray> nodeArrays = new HashMap<>(); // keyed by NodeArrayId
    private final Map<GlobalNodeId, Host> hosts = new HashMap<>(); // keyed by HostId
    private final Timer hostsCheckerTimer = new Timer();
    private TestingServer zkServer;
    private CuratorFramework curator;
    private ClusterTools clusterTools;

    public Cluster(ClusterConfiguration configuration) throws Exception
    {
        this(generateId(), configuration);
    }

    private static String generateId()
    {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String className = stackTrace[3].getClassName();
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        return simpleClassName + "_" + stackTrace[3].getMethodName();
    }

    public Cluster(String id, ClusterConfiguration configuration) throws Exception
    {
        this.id = id;
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
        clusterTools = new ClusterTools(curator, new GlobalNodeId(id, LocalHostLauncher.HOSTNAME));

        // start all host nodes
        List<String> hostnames = configuration.nodeArrays().stream()
            .flatMap(cfg -> cfg.nodes().stream())
            .map(Node::getHostname)
            .distinct()
            .collect(Collectors.toList());
        for (String hostname : hostnames)
        {
            GlobalNodeId globalNodeId = new GlobalNodeId(id, hostname);
            HostLauncher launcher = hostname.equals(LocalHostLauncher.HOSTNAME) ? localHostLauncher : hostLauncher;
            if (launcher == null)
                throw new IllegalStateException("No configured host launcher to start node on " + hostname);
            String remoteConnectString = launcher.launch(globalNodeId, connectString);
            hosts.put(globalNodeId, new Host(globalNodeId, new RpcClient(curator, globalNodeId), remoteConnectString));
        }

        // start all worker nodes
        for (NodeArrayConfiguration nodeArrayConfig : configuration.nodeArrays())
        {
            Map<String, NodeArray.Node> nodeArrayNodes = new HashMap<>();
            for (Node nodeConfig : nodeArrayConfig.nodes())
            {
                GlobalNodeId globalNodeId = new GlobalNodeId(id, nodeArrayConfig, nodeConfig);
                Host host = hosts.get(globalNodeId.getHostGlobalId());
                try
                {
                    NodeProcess remoteProcess = (NodeProcess)host.rpcClient.callAsync(new SpawnNodeCommand(nodeArrayConfig.jvm(), globalNodeId.getHostId(), globalNodeId.getNodeId(), host.remoteConnectString)).get(10, TimeUnit.SECONDS);
                    NodeArray.Node node = new NodeArray.Node(globalNodeId, remoteProcess, new RpcClient(curator, globalNodeId));
                    host.nodes.add(node);
                    nodeArrayNodes.put(nodeConfig.getId(), node);
                }
                catch (Exception e)
                {
                    throw new Exception("Error spawning node '" + globalNodeId.getHostId() + "'", e);
                }
            }
            nodeArrays.put(nodeArrayConfig.id(), new NodeArray(nodeArrayNodes));
        }

        // start heath checker timer
        hostsCheckerTimer.schedule(new TimerTask() {
            @Override
            public void run()
            {
                for (Host host : hosts.values())
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("Checking host {}", host);
                    host.check();
                }
            }
        }, 5000, 5000);
    }

    public ClusterTools tools()
    {
        return clusterTools;
    }

    @Override
    public void close()
    {
        hostsCheckerTimer.cancel();
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
        private final GlobalNodeId globalNodeId;
        private final RpcClient rpcClient;
        private final String remoteConnectString;
        private final List<NodeArray.Node> nodes = new ArrayList<>();

        private Host(GlobalNodeId globalNodeId, RpcClient rpcClient, String remoteConnectString)
        {
            this.globalNodeId = globalNodeId;
            this.rpcClient = rpcClient;
            this.remoteConnectString = remoteConnectString;
        }

        private void check()
        {
            boolean allSane = true;
            for (NodeArray.Node node : nodes)
            {
                NodeProcess nodeProcess = node.getNodeProcess();
                try
                {
                    rpcClient.call(new CheckNodeCommand(nodeProcess));
                }
                catch (Exception e)
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("Host {} failed check of {}", globalNodeId.getHostId(), nodeProcess, e);
                    allSane = false;
                }
            }
            if (!allSane)
                close();
        }

        @Override
        public void close()
        {
            for (NodeArray.Node node : nodes)
            {
                NodeProcess nodeProcess = node.getNodeProcess();
                try
                {
                    rpcClient.call(new KillNodeCommand(nodeProcess));
                }
                catch (Exception e)
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("Error closing {}", nodeProcess, e);
                }
            }
            IOUtil.close(rpcClient);
            nodes.forEach(IOUtil::close);
            nodes.clear();
        }

        @Override
        public String toString()
        {
            return "Host{" +
                "globalNodeId='" + globalNodeId + '\'' +
                "remoteConnectString='" + remoteConnectString + '\'' +
                ", nodes=" + nodes +
                '}';
        }
    }
}
