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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.barriers.DistributedDoubleBarrier;
import org.mortbay.jetty.orchestrator.rpc.GlobalNodeId;

public class Barrier
{
    private final DistributedDoubleBarrier distributedDoubleBarrier;
    private final AtomicCounter atomicCounter;
    private final int parties;
    private final AtomicBoolean guard = new AtomicBoolean();

    public Barrier(CuratorFramework curator, GlobalNodeId globalNodeId, String name, int parties)
    {
        this.parties = parties;
        distributedDoubleBarrier = new DistributedDoubleBarrier(curator, "/clients/" + globalNodeId.getClusterId() + "/Barrier/" + name, parties);
        atomicCounter = new AtomicCounter(curator, globalNodeId, "BarrierCounter", name, parties);
    }

    public int await() throws Exception
    {
        if (!guard.compareAndSet(false, true))
            throw new BrokenBarrierException("Barrier is not cyclic");
        distributedDoubleBarrier.enter();
        int index = (int)atomicCounter.decrementAndGet();
        if (index == 0)
            atomicCounter.set(parties);
        distributedDoubleBarrier.leave();
        return index;
    }

    public int await(long timeout, TimeUnit unit) throws Exception
    {
        if (!guard.compareAndSet(false, true))
            throw new BrokenBarrierException("Barrier is not cyclic");
        boolean success = distributedDoubleBarrier.enter(timeout, unit);
        if (!success)
            throw new TimeoutException("Timeout awaiting on barrier");
        int index = (int)atomicCounter.decrementAndGet();
        if (index == 0)
            atomicCounter.set(parties);
        distributedDoubleBarrier.leave();
        return index;
    }
}
