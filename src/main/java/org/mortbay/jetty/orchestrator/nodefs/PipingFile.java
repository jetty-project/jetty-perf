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

package org.mortbay.jetty.orchestrator.nodefs;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import net.schmizz.sshj.xfer.InMemoryDestFile;

class PipingFile extends InMemoryDestFile
{
    private final PipedOutputStream outputStream = new PipedOutputStream();
    private final PipedInputStream inputStream;

    public PipingFile() throws IOException
    {
        inputStream = new PipedInputStream(outputStream);
    }

    @Override
    public PipedOutputStream getOutputStream()
    {
        return outputStream;
    }

    public PipedInputStream getInputStream()
    {
        return inputStream;
    }
}
