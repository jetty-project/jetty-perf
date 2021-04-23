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

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.barriers.DistributedDoubleBarrier;

public class Barrier
{
    private final DistributedDoubleBarrier distributedDoubleBarrier;
    private final AtomicCounter atomicCounter;

    public Barrier(CuratorFramework curator, String nodeId, String name, int parties)
    {
        distributedDoubleBarrier = new DistributedDoubleBarrier(curator, "/clients/" + clusterIdOf(nodeId) + "/Barrier/" + name, parties);
        atomicCounter = new AtomicCounter(curator, nodeId, "Barrier/Counter", name, parties);
    }

    private static String clusterIdOf(String nodeId)
    {
        return nodeId.split("/")[0];
    }

    public int await() throws Exception
    {
        if (atomicCounter.get() == 0L)
            throw new BrokenBarrierException("Barrier is not cyclic");
        distributedDoubleBarrier.enter();
        return (int)atomicCounter.decrementAndGet();
    }

    public int await(long timeout, TimeUnit unit) throws Exception
    {
        if (atomicCounter.get() == 0L)
            throw new BrokenBarrierException("Barrier is not cyclic");
        distributedDoubleBarrier.enter(timeout, unit);
        return (int)atomicCounter.decrementAndGet();
    }
}
