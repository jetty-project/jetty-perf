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

package org.mortbay.jetty.orchestrator.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.mortbay.jetty.orchestrator.nodefs.NodeFileSystemProvider;
import org.mortbay.jetty.orchestrator.rpc.NodeProcess;
import org.mortbay.jetty.orchestrator.util.IOUtil;

public class LocalHostLauncher implements HostLauncher
{
    public static final String HOSTNAME = "localhost";

    private Thread thread;
    private String hostId;

    @Override
    public String launch(String hostname, String hostId, String connectString) throws Exception
    {
        if (!HOSTNAME.equals(hostname))
            throw new IllegalArgumentException("local launcher can only work with 'localhost' hostname");
        if (thread != null)
            throw new IllegalStateException("local launcher already spawned 'localhost' thread");
        this.hostId = hostId;

        String[] classpathEntries = System.getProperty("java.class.path").split(File.pathSeparator);
        for (String classpathEntry : classpathEntries)
        {
            File cpFile = new File(classpathEntry);
            if (cpFile.isDirectory())
            {
                copyDir(hostId, cpFile, 1);
            }
            else
            {
                String filename = cpFile.getName();
                try (InputStream is = new FileInputStream(cpFile))
                {
                    copyFile(hostId, filename, is);
                }
            }
        }

        try
        {
            this.thread = NodeProcess.spawnThread(hostId, connectString);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return connectString;
    }

    @Override
    public void close() throws Exception
    {
        if (thread != null)
        {
            thread.interrupt();
            thread.join();
            thread = null;

            File rootPath = rootPathOf(hostId);
            File parentPath = rootPath.getParentFile();
            if (IOUtil.deltree(rootPath) && parentPath != null)
            {
                String[] files = parentPath.list();
                if (files != null && files.length == 0)
                    IOUtil.deltree(parentPath);
            }
            hostId = null;
        }
    }

    public static File rootPathOf(String hostId)
    {
        return new File(System.getProperty("user.home") + "/." + NodeFileSystemProvider.PREFIX + "/" + hostId);
    }

    private void copyFile(String hostId, String filename, InputStream contents) throws Exception
    {
        File rootPath = rootPathOf(hostId);
        File libPath = new File(rootPath, NodeProcess.CLASSPATH_FOLDER_NAME);

        File file = new File(libPath, filename);
        file.getParentFile().mkdirs();
        try (OutputStream fos = new FileOutputStream(file))
        {
            IOUtil.copy(contents, fos);
        }
    }

    private void copyDir(String hostId, File cpFile, int depth) throws Exception
    {
        File[] files = cpFile.listFiles();
        if (files == null)
            return;

        for (File file : files)
        {
            if (file.isDirectory())
            {
                copyDir(hostId, file, depth + 1);
            }
            else
            {

                String filename = file.getName();
                File currentFile = file;
                for (int i = 0; i < depth; i++)
                {
                    currentFile = currentFile.getParentFile();
                    filename = currentFile.getName() + "/" + filename;
                }
                try (InputStream is = new FileInputStream(file))
                {
                    copyFile(hostId, filename, is);
                }
            }
        }
    }
}
