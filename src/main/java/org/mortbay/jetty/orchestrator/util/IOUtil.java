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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IOUtil
{
    private static final Logger LOG = LoggerFactory.getLogger(IOUtil.class);

    public static void close(AutoCloseable closeable)
    {
        try
        {
            if (closeable != null)
                closeable.close();
        }
        catch (Exception e)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("", e);
        }
    }

    public static void copy(InputStream is, OutputStream os) throws IOException
    {
        copy(is, os, 1024);
    }

    public static void copy(InputStream is, OutputStream os, int bufferSize) throws IOException
    {
        byte[] buffer = new byte[bufferSize];
        while (true)
        {
            int read = is.read(buffer);
            if (read == -1)
            {
                return;
            }
            os.write(buffer, 0, read);
        }
    }

    public static boolean deltree(File folder) {
        File[] files = folder.listFiles();
        if (files != null)
        {
            for (File file : files) {
                deltree(file);
            }
        }
        return folder.delete();
    }
}
