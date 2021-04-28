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

public class Response implements Serializable
{
    private final long id;
    private final Object result;
    private final Throwable throwable;

    public Response(long id, Object result, Throwable throwable)
    {
        this.id = id;
        this.result = result;
        this.throwable = throwable;
    }

    public long getId()
    {
        return id;
    }

    public Object getResult()
    {
        return result;
    }

    public Throwable getThrowable()
    {
        return throwable;
    }

    @Override
    public String toString()
    {
        return "Response{" +
            "id=" + id +
            ", result=" + result +
            ", throwable=" + throwable +
            '}';
    }
}
