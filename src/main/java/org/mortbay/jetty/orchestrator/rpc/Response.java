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
    private final Exception exception;

    public Response(long id, Object result, Exception exception)
    {
        this.id = id;
        this.result = result;
        this.exception = exception;
    }

    public long getId()
    {
        return id;
    }

    public Object getResult()
    {
        return result;
    }

    public Exception getException()
    {
        return exception;
    }

    @Override
    public String toString()
    {
        return "Response{" +
            "id=" + id +
            ", result=" + result +
            ", exception=" + exception +
            '}';
    }
}
