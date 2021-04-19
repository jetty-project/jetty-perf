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

import java.nio.ByteBuffer;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.atomic.PromotedToLock;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.KeeperException;

public class AtomicCounter
{
    private final DistributedAtomicLong distributedAtomicLong;
    private final String nodeId;
    private final String name;

    public AtomicCounter(CuratorFramework curator, String nodeId, String name, long initialValue)
    {
        this(curator, nodeId, "AtomicCounter", name, initialValue);
    }

    AtomicCounter(CuratorFramework curator, String nodeId, String internalPath, String name, long initialValue)
    {
        this.nodeId = nodeId;
        this.name = name;
        String prefix = "/clients/" + clusterIdOf(nodeId) + "/" + internalPath;
        String counterName = prefix + "/" + name;
        String lockName = prefix + "/Lock/" + name;
        try
        {
            byte[] initialBytes = new byte[Long.BYTES];
            ByteBuffer.wrap(initialBytes).putLong(initialValue);
            curator.create().creatingParentsIfNeeded().forPath(counterName, initialBytes);
        }
        catch (KeeperException.NodeExistsException e)
        {
            // node already exists, no need to set its initial value
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Error accessing AtomicCounter " + counterName);
        }
        this.distributedAtomicLong = new DistributedAtomicLong(curator,
            counterName,
            new RetryNTimes(0, 0),
            PromotedToLock.builder().lockPath(lockName).build());
    }

    private static String clusterIdOf(String nodeId)
    {
        return nodeId.split("/")[0];
    }

    public boolean compareAndSet(long expectedValue, long newValue)
    {
        try
        {
            AtomicValue<Long> result = distributedAtomicLong.compareAndSet(expectedValue, newValue);
            return result.succeeded();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("node " + nodeId + " failed to compare and set counter " + name, e);
        }
    }

    public long incrementAndGet()
    {
        try
        {
            AtomicValue<Long> result = distributedAtomicLong.add(1L);
            if (result.succeeded())
                return result.postValue();
            throw new IllegalStateException("node " + nodeId + " failed to increment and get counter " + name);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("node " + nodeId + " failed to increment and get counter " + name, e);
        }
    }

    public long getAndIncrement()
    {
        try
        {
            AtomicValue<Long> result = distributedAtomicLong.add(1L);
            if (result.succeeded())
                return result.preValue();
            throw new IllegalStateException("node " + nodeId + " failed to get and increment counter " + name);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("node " + nodeId + " failed to get and increment counter " + name, e);
        }
    }

    public long get()
    {
        try
        {
            AtomicValue<Long> result = distributedAtomicLong.get();
            if (result.succeeded())
                return result.postValue();
            throw new IllegalStateException("node " + nodeId + " failed to get counter " + name);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("node " + nodeId + " failed to get counter " + name, e);
        }
    }

    public void set(long value)
    {
        try
        {
            distributedAtomicLong.forceSet(value);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("node " + nodeId + " failed to set counter " + name, e);
        }
    }
}
