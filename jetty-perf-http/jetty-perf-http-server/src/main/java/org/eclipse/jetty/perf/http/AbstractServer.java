//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.perf.http;

public abstract class AbstractServer
{
    private final int maxThreads;
    private final int port;
    private final int selectors;

    public AbstractServer(int maxThreads, int port, int selectors)
    {
        this.maxThreads = maxThreads;
        this.port = port;
        this.selectors = selectors;
    }

    public int getMaxThreads()
    {
        return maxThreads;
    }

    public int getPort()
    {
        return port;
    }

    public int getSelectors()
    {
        return selectors;
    }

    public abstract void accept() throws Exception;
}
