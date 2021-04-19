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

package org.mortbay.jetty.orchestrator.rpc.command;

import org.mortbay.jetty.orchestrator.ClusterTools;
import org.mortbay.jetty.orchestrator.configuration.Jvm;
import org.mortbay.jetty.orchestrator.rpc.NodeProcess;

public class SpawnNodeCommand implements Command
{
    private final Jvm jvm;
    private final String hostId;
    private final String nodeId;
    private final String connectString;

    public SpawnNodeCommand(Jvm jvm, String hostId, String nodeId, String connectString)
    {
        this.jvm = jvm;
        this.hostId = hostId;
        this.nodeId = nodeId;
        this.connectString = connectString;
    }

    @Override
    public NodeProcess execute(ClusterTools clusterTools)
    {
        try
        {
            return NodeProcess.spawn(jvm, hostId, nodeId, connectString);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
