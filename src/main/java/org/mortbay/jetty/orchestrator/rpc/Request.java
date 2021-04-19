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

import java.io.Serializable;

import org.mortbay.jetty.orchestrator.rpc.command.Command;

public class Request implements Serializable
{
    private final long id;
    private final Command command;

    public Request(long id, Command command)
    {
        this.id = id;
        this.command = command;
    }

    public long getId()
    {
        return id;
    }

    public Command getCommand()
    {
        return command;
    }

    @Override
    public String toString()
    {
        return "Request{" +
            "id=" + id +
            ", command=" + command +
            '}';
    }
}
