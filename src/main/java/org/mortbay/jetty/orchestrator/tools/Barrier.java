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
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;

public class Barrier
{
    private final DistributedBarrier distributedBarrier;
    private final AtomicCounter atomicCounter;
    private final int parties;

    public Barrier(CuratorFramework curator, String nodeId, String name, int parties)
    {
        try
        {
            this.parties = parties;
            distributedBarrier = new DistributedBarrier(curator, "/clients/" + clusterIdOf(nodeId) + "/Barrier/" + name);
            distributedBarrier.setBarrier();
            atomicCounter = new AtomicCounter(curator, nodeId, "Barrier/Counter", name, parties);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Error initializing barrier '" + name + "'");
        }
    }

    private static String clusterIdOf(String nodeId)
    {
        return nodeId.split("/")[0];
    }

    private int calculateArrivalIndex()
    {
        int arrivalIndex = (int)atomicCounter.decrementAndGet();
        if (arrivalIndex == 0)
            atomicCounter.set(parties);
        return arrivalIndex;
    }

    public int await() throws Exception
    {
        int arrival = calculateArrivalIndex();
        if (arrival == 0)
            distributedBarrier.removeBarrier();
        else
            distributedBarrier.waitOnBarrier();
        return arrival;
    }

    public int await(long timeout, TimeUnit unit) throws Exception
    {
        int arrival = calculateArrivalIndex();
        if (arrival == 0)
            distributedBarrier.removeBarrier();
        else
            distributedBarrier.waitOnBarrier(timeout, unit);
        return arrival;
    }
}
