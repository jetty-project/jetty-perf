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

public class InteractiveAsyncServer92 extends AbstractInteractiveServer
{
    public static void main(String[] args) throws Exception
    {
        new InteractiveAsyncServer92().interact();
    }

    @Override
    protected AbstractServer newServer(int maxThreads, int port, int selectors)
    {
        return new AsyncServer92(maxThreads, port, selectors);
    }
}
