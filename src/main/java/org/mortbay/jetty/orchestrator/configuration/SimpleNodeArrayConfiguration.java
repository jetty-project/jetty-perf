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

public class SimpleNodeArrayConfiguration implements NodeArrayConfiguration, JvmDependent
{
    private final String id;
    private Jvm jvm;
    private NodeArrayTopology nodeArrayTopology = new NodeArrayTopology();

    public SimpleNodeArrayConfiguration(String id)
    {
        this.id = id;
    }

    @Override
    public String id()
    {
        return id;
    }

    @Override
    public Jvm jvm()
    {
        return jvm;
    }

    @Override
    public NodeArrayTopology topology()
    {
        return nodeArrayTopology;
    }

    public SimpleNodeArrayConfiguration jvm(Jvm jvm)
    {
        this.jvm = jvm;
        return this;
    }

    public SimpleNodeArrayConfiguration topology(NodeArrayTopology nodeArrayTopology)
    {
        this.nodeArrayTopology = nodeArrayTopology;
        return this;
    }
}
