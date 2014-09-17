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
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.toolchain.perf.PlatformMonitor;

public abstract class AbstractInteractiveClient
{
    public void interact() throws Exception
    {
        PlatformMonitor monitor = new PlatformMonitor();
        PrintStream consoleOut = System.err;
        BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in));

        String host = "localhost";
        consoleOut.printf("server [%s]: ", host);
        String value = consoleIn.readLine().trim();
        if (value.isEmpty())
            value = host;
        host = value;

        int port = 8080;
        consoleOut.printf("port [%d]: ", port);
        value = consoleIn.readLine().trim();
        if (value.isEmpty())
            value = String.valueOf(port);
        port = Integer.parseInt(value);

        int senders = 1;
        consoleOut.printf("senders [%d]: ", senders);
        value = consoleIn.readLine().trim();
        if (value.isEmpty())
            value = String.valueOf(senders);
        senders = Integer.parseInt(value);

        int connections = 1000;
        consoleOut.printf("connections [%d]: ", connections);
        value = consoleIn.readLine().trim();
        if (value.isEmpty())
            value = String.valueOf(connections);
        connections = Integer.parseInt(value);

        consoleOut.printf("connecting... ");
        AbstractClient client = newClient(host, port, connections, senders);
        client.connect();
        consoleOut.printf("done%n");

        int iterations = 15;
        long pause = TimeUnit.SECONDS.toMicros(1) / connections;
        int length = 1024;

        while (true)
        {
            consoleOut.printf("iterations [%d]: ", iterations);
            value = consoleIn.readLine().trim();
            if (value.isEmpty())
                value = String.valueOf(iterations);
            iterations = Integer.parseInt(value);

            if (iterations <= 0)
                break;

            consoleOut.printf("pause (\u00b5s) [%d]: ", pause);
            value = consoleIn.readLine().trim();
            if (value.length() == 0)
                value = String.valueOf(pause);
            pause = Long.parseLong(value);

            consoleOut.printf("response length [%d]: ", length);
            value = consoleIn.readLine().trim();
            if (value.isEmpty())
                value = String.valueOf(length);
            length = Integer.parseInt(value);

            consoleOut.println(monitor.start());
            Result result = client.benchmark(iterations, pause, length);
            consoleOut.println(monitor.stop());
            consoleOut.printf("%s%n", result);
        }

        consoleOut.printf("disconnecting... ");
        client.disconnect();
        consoleOut.printf("terminated%n");
    }

    protected abstract AbstractClient newClient(String host, int port, int connections, int senders) throws Exception;
}
