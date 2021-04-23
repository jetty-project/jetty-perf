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

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.mortbay.jetty.orchestrator.configuration.LocalHostLauncher;
import org.mortbay.jetty.orchestrator.nodefs.NodeFileSystemProvider;
import org.mortbay.jetty.orchestrator.rpc.RpcClient;
import org.mortbay.jetty.orchestrator.rpc.command.ExecuteNodeJobCommand;

public class NodeArray
{
    private final Map<String, Node> nodes;

    NodeArray(Map<String, Node> nodes)
    {
        this.nodes = nodes;
    }

    public String hostnameOf(String id)
    {
        Node node = nodes.get(id);
        if (node == null)
            throw new IllegalArgumentException("No such node with ID " + id);
        return node.hostname;
    }

    public Path rootPathOf(String id)
    {
        Node node = nodes.get(id);
        if (node == null)
            throw new IllegalArgumentException("No such node with ID " + id);
        if (node.local)
        {
            return LocalHostLauncher.rootPathOf(node.nodeId).toPath();
        }
        else
        {
            URI uri = URI.create(NodeFileSystemProvider.PREFIX + ":" + node.nodeId + "!/");
            return Paths.get(uri);
        }
    }

    public Set<String> ids()
    {
        return nodes.keySet();
    }

    public NodeArrayFuture executeOnAll(NodeJob nodeJob) throws Exception
    {
        List<CompletableFuture<Object>> futures = new ArrayList<>();
        for (Node node : nodes.values())
        {
            CompletableFuture<Object> future = node.rpcClient.callAsync(new ExecuteNodeJobCommand(nodeJob));
            futures.add(future);
        }
        return new NodeArrayFuture(futures);
    }

    static class Node
    {
        private final String hostname;
        private final String nodeId;
        private final RpcClient rpcClient;
        private final boolean local;

        Node(String hostname, String nodeId, RpcClient rpcClient, boolean local)
        {
            this.hostname = hostname;
            this.nodeId = nodeId;
            this.rpcClient = rpcClient;
            this.local = local;
        }
    }
}
