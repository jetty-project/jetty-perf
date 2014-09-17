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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;

public abstract class AbstractInteractiveServer
{
    public void interact() throws Exception
    {
        PrintStream consoleOut = System.err;
        BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in));

        int port = 8080;
        consoleOut.printf("listen port [%d]: ", port);
        String value = consoleIn.readLine().trim();
        if (value.isEmpty())
            value = String.valueOf(port);
        port = Integer.parseInt(value);

        int selectors = Runtime.getRuntime().availableProcessors();
        System.err.printf("selectors [%d]: ", selectors);
        value = consoleIn.readLine().trim();
        if (value.length() == 0)
            value = String.valueOf(selectors);
        selectors = Integer.parseInt(value);

        int maxThreads = 512;
        System.err.printf("max threads [%d]: ", maxThreads);
        value = consoleIn.readLine().trim();
        if (value.length() == 0)
            value = String.valueOf(maxThreads);
        maxThreads = Integer.parseInt(value);

        AbstractServer server = newServer(maxThreads, port, selectors);
        server.accept();

        consoleOut.printf("server ready on port %d%n", port);
    }

    protected abstract AbstractServer newServer(int maxThreads, int port, int selectors);
}
