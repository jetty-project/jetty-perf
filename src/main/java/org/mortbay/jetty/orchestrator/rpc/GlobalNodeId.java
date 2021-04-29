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

package org.mortbay.jetty.orchestrator.rpc;

import java.util.Objects;

import org.apache.curator.utils.ZKPaths;
import org.mortbay.jetty.orchestrator.configuration.LocalHostLauncher;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;

public class GlobalNodeId
{
    private final String clusterId;
    private final String hostname;
    private final String hostId;
    private final String nodeId;
    private final boolean local;

    public GlobalNodeId(String clusterId, NodeArrayConfiguration nodeArrayConfiguration, Node node)
    {
        this.clusterId = sanitize(clusterId);
        this.hostname = node.getHostname();
        this.hostId = this.clusterId + ZKPaths.PATH_SEPARATOR + sanitize(hostname);
        this.nodeId = hostId + ZKPaths.PATH_SEPARATOR + sanitize(nodeArrayConfiguration.id()) + ZKPaths.PATH_SEPARATOR + sanitize(node.getId());
        this.local = hostname.equals(LocalHostLauncher.HOSTNAME);
    }

    public GlobalNodeId(String clusterId, String hostname)
    {
        this.clusterId = sanitize(clusterId);
        this.hostname = hostname;
        this.hostId = this.clusterId + ZKPaths.PATH_SEPARATOR + sanitize(hostname);
        this.nodeId = hostId;
        this.local = hostname.equals(LocalHostLauncher.HOSTNAME);
    }

    public GlobalNodeId(String nodeId)
    {
        String[] parts = nodeId.split(ZKPaths.PATH_SEPARATOR);
        if (parts.length != 2 && parts.length != 4)
            throw new IllegalArgumentException("Invalid global node id : '" + nodeId + "'");
        this.clusterId = parts[0];
        this.hostname = null;
        if (parts.length == 2)
        {
            this.hostId = nodeId;
            this.nodeId = nodeId;
        }
        else
        {
            this.hostId = clusterId + ZKPaths.PATH_SEPARATOR + parts[1];
            this.nodeId = nodeId;
        }
        this.local = parts[1].equals(LocalHostLauncher.HOSTNAME);
    }

    private static String sanitize(String id)
    {
        return id.replace(":", "_")
            .replace(ZKPaths.PATH_SEPARATOR, "_");
    }

    public String getClusterId()
    {
        return clusterId;
    }

    public String getHostname()
    {
        return hostname;
    }

    public String getHostId()
    {
        return hostId;
    }

    public String getNodeId()
    {
        return nodeId;
    }

    public boolean isLocal()
    {
        return local;
    }

    public GlobalNodeId getHostGlobalId()
    {
        return new GlobalNodeId(clusterId, hostname);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GlobalNodeId that = (GlobalNodeId)o;
        return nodeId.equals(that.nodeId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(nodeId);
    }
}
