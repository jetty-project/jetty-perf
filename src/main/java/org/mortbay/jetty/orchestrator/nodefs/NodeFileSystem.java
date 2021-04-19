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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;

public class NodeFileSystem extends FileSystem
{
    private final NodeFileSystemProvider provider;
    private final SFTPClient sftpClient;
    private final Path rootPath;
    private volatile boolean closed;

    public NodeFileSystem(NodeFileSystemProvider provider, SFTPClient sftpClient, String nodeId, String root)
    {
        this.provider = provider;
        this.sftpClient = sftpClient;
        this.rootPath = new NodePath(this, nodeId, root);
    }

    SeekableByteChannel newByteChannel(NodePath path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException
    {
        String sftpPath = ".wtc/" + path.getNodeId() + path.getRealPath();
        InMemoryFile inMemoryFile = new InMemoryFile();
        sftpClient.get(sftpPath, inMemoryFile);
        byte[] data = inMemoryFile.getOutputStream().toByteArray();

        return new SeekableByteChannel()
        {
            private long position;

            @Override
            public void close()
            {
            }

            @Override
            public boolean isOpen()
            {
                return true;
            }

            @Override
            public long position()
            {
                return position;
            }

            @Override
            public SeekableByteChannel position(long newPosition)
            {
                position = newPosition;
                return this;
            }

            @Override
            public int read(ByteBuffer dst)
            {
                int l = (int)Math.min(dst.remaining(), size() - position);
                dst.put(data, (int)position, l);
                position += l;
                return l;
            }

            @Override
            public long size()
            {
                return data.length;
            }

            @Override
            public SeekableByteChannel truncate(long size)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public int write(ByteBuffer src)
            {
                throw new UnsupportedOperationException();
            }
        };
    }

    DirectoryStream<Path> newDirectoryStream(NodePath dir, DirectoryStream.Filter<? super Path> filter) throws IOException
    {
        String sftpPath = ".wtc/" + dir.getNodeId() + dir.getRealPath();
        List<RemoteResourceInfo> content = sftpClient.ls(sftpPath);
        List<Path> filteredPaths = new ArrayList<>();
        for (RemoteResourceInfo remoteResourceInfo : content)
        {
            Path resolved = dir.resolve(remoteResourceInfo.getName());
            if (filter.accept(resolved))
                filteredPaths.add(resolved);
        }

        return new DirectoryStream<Path>()
        {
            @Override
            public Iterator<Path> iterator()
            {
                return new Iterator<Path>()
                {
                    private final Iterator<Path> delegate = filteredPaths.iterator();

                    @Override
                    public boolean hasNext()
                    {
                        return delegate.hasNext();
                    }

                    @Override
                    public Path next()
                    {
                        return delegate.next();
                    }

                    @Override
                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                };
            }
            @Override
            public void close()
            {
            }
        };
    }

    InputStream newInputStream(NodePath path, OpenOption... options) throws IOException
    {
        String sftpPath = ".wtc/" + path.getNodeId() + path.getRealPath();
        InMemoryFile inMemoryFile = new InMemoryFile();
        sftpClient.get(sftpPath, inMemoryFile);
        byte[] data = inMemoryFile.getOutputStream().toByteArray();
        return new ByteArrayInputStream(data);
    }

    @Override
    public FileSystemProvider provider()
    {
        return provider;
    }

    @Override
    public void close() throws IOException
    {
        sftpClient.close();
        closed = true;
    }

    @Override
    public boolean isOpen()
    {
        return !closed;
    }

    @Override
    public boolean isReadOnly()
    {
        return true;
    }

    @Override
    public String getSeparator()
    {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories()
    {
        return Collections.singleton(rootPath);
    }

    @Override
    public Iterable<FileStore> getFileStores()
    {
        return Collections.emptySet();
    }

    @Override
    public Set<String> supportedFileAttributeViews()
    {
        return Collections.emptySet();
    }

    @Override
    public Path getPath(String first, String... more)
    {
        return null;
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService()
    {
        throw new UnsupportedOperationException();
    }
}
