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

package org.mortbay.jetty.orchestrator.configuration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SimpleClusterConfiguration implements ClusterConfiguration, JvmDependent
{
    private static final Jvm DEFAULT_JVM = new Jvm(() -> "java");

    private Jvm jvm = DEFAULT_JVM;
    private final Map<String, NodeArrayConfiguration> nodeArrayConfigurations = new HashMap<>();
    private HostLauncher hostLauncher = new SshRemoteHostLauncher();

    public SimpleClusterConfiguration jvm(Jvm jvm)
    {
        this.jvm = jvm;
        ensureJvmSet(hostLauncher);
        nodeArrayConfigurations.values().forEach(this::ensureJvmSet);
        return this;
    }

    @Override
    public Jvm jvm()
    {
        return jvm;
    }

    @Override
    public Collection<NodeArrayConfiguration> nodeArrays()
    {
        return nodeArrayConfigurations.values();
    }

    @Override
    public HostLauncher hostLauncher()
    {
        return hostLauncher;
    }

    public SimpleClusterConfiguration nodeArray(NodeArrayConfiguration nodeArrayConfiguration)
    {
        String id = nodeArrayConfiguration.id();
        if (nodeArrayConfigurations.containsKey(id))
            throw new IllegalArgumentException("Duplicate node array ID: " + id);
        nodeArrayConfigurations.put(id, nodeArrayConfiguration);
        ensureJvmSet(nodeArrayConfiguration);
        return this;
    }

    public SimpleClusterConfiguration hostLauncher(HostLauncher hostLauncher)
    {
        this.hostLauncher = hostLauncher;
        ensureJvmSet(hostLauncher);
        return this;
    }

    private void ensureJvmSet(Object obj)
    {
        if (obj instanceof JvmDependent)
        {
            JvmDependent jvmDependent = (JvmDependent)obj;
            if (jvmDependent.jvm() == null)
                jvmDependent.jvm(jvm);
        }
    }
}
