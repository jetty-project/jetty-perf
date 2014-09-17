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

import org.eclipse.jetty.toolchain.perf.PlatformTimer;

public abstract class AbstractClient
{
    private final PlatformTimer timer = PlatformTimer.detect();
    private final String host;
    private final int port;
    private final int connections;
    private final int senders;

    protected AbstractClient(String host, int port, int connections, int senders)
    {
        this.host = host;
        this.port = port;
        this.connections = connections;
        this.senders = senders;
    }

    public PlatformTimer getPlatformTimer()
    {
        return timer;
    }

    public String getHost()
    {
        return host;
    }

    public int getPort()
    {
        return port;
    }

    public int getConnections()
    {
        return connections;
    }

    public int getSenders()
    {
        return senders;
    }

    public abstract void connect() throws Exception;

    public abstract Result benchmark(int iterations, long pause, int length) throws Exception;

    public abstract void disconnect() throws Exception;
}
