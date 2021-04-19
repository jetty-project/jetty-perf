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
import org.mortbay.jetty.orchestrator.NodeJob;

public class ExecuteNodeJobCommand implements Command
{
    private final NodeJob nodeJob;

    public ExecuteNodeJobCommand(NodeJob nodeJob)
    {
        this.nodeJob = nodeJob;
    }

    @Override
    public Object execute(ClusterTools clusterTools) throws Exception
    {
        nodeJob.execute(clusterTools);
        return null;
    }
}
