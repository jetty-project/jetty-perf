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

package org.mortbay.jetty.orchestrator.tools;

import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.barriers.DistributedDoubleBarrier;

public class Barrier
{
    private final DistributedDoubleBarrier distributedDoubleBarrier;
    private final AtomicCounter atomicCounter;
    private final int parties;

    public Barrier(CuratorFramework curator, String nodeId, String name, int parties)
    {
        this.parties = parties;
        distributedDoubleBarrier = new DistributedDoubleBarrier(curator, "/clients/" + clusterIdOf(nodeId) + "/Barrier/" + name, parties);
        atomicCounter = new AtomicCounter(curator, nodeId, "Barrier", name + "/Counter", parties);
    }

    private static String clusterIdOf(String nodeId)
    {
        return nodeId.split("/")[0];
    }

    public int await() throws Exception
    {
        int index = (int)atomicCounter.decrementAndGet();
        distributedDoubleBarrier.enter();
        if (index == 0)
            atomicCounter.set(parties);
        return index;
    }

    public int await(long timeout, TimeUnit unit) throws Exception
    {
        int index = (int)atomicCounter.decrementAndGet();
        distributedDoubleBarrier.enter(timeout, unit);
        if (index == 0)
            atomicCounter.set(parties);
        return index;
    }
}
