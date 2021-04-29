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

package org.mortbay.jetty.orchestrator.util;

public class ByteBuilder
{
    private final byte[] buffer;
    private int length = 0;

    public ByteBuilder(int buffSize)
    {
        this.buffer = new byte[buffSize];
    }

    public void append(int b)
    {
        if (isFull())
            throw new IllegalStateException("buffer is full");
        buffer[length] = (byte)b;
        length++;
    }

    public void clear()
    {
        length = 0;
    }

    public byte[] getBuffer()
    {
        return buffer;
    }

    public boolean isFull()
    {
        return length == buffer.length;
    }

    public int length()
    {
        return length;
    }
}
