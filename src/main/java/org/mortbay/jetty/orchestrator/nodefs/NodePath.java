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

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

public class NodePath implements Path
{
    private final NodeFileSystem fileSystem;
    private final String nodeId;
    private final String[] pathSegments;

    NodePath(NodeFileSystem fileSystem, String nodeId, String path)
    {
        this(fileSystem, nodeId, path.split("/"));
    }

    private NodePath(NodeFileSystem fileSystem, String nodeId, String[] pathSegments)
    {
        this.fileSystem = fileSystem;
        this.nodeId = nodeId;
        this.pathSegments = pathSegments;
    }

    public String getNodeId()
    {
        return nodeId;
    }

    public String getRealPath()
    {
        return "/" + String.join("/", pathSegments);
    }

    @Override
    public FileSystem getFileSystem()
    {
        return fileSystem;
    }

    @Override
    public boolean isAbsolute()
    {
        return true;
    }

    @Override
    public Path getRoot()
    {
        return fileSystem.getRootDirectories().iterator().next();
    }

    @Override
    public Path getFileName()
    {
        return this;
    }

    @Override
    public Path getParent()
    {
        if (pathSegments.length == 0)
            return this;
        String[] parent = new String[pathSegments.length - 1];
        System.arraycopy(pathSegments, 0, parent, 0, parent.length);
        return new NodePath(fileSystem, nodeId, parent);
    }

    @Override
    public int getNameCount()
    {
        return pathSegments.length;
    }

    @Override
    public Path getName(int index)
    {
        if (index > pathSegments.length)
            throw new IllegalArgumentException("index " + index + " too big for " + this);
        if (index == pathSegments.length)
            return this;
        String[] parent = new String[pathSegments.length - index];
        System.arraycopy(pathSegments, 0, parent, 0, parent.length);
        return new NodePath(fileSystem, nodeId, parent);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex)
    {
        return null;
    }

    @Override
    public boolean startsWith(Path other)
    {
        return false;
    }

    @Override
    public boolean startsWith(String other)
    {
        return false;
    }

    @Override
    public boolean endsWith(Path other)
    {
        return false;
    }

    @Override
    public boolean endsWith(String other)
    {
        return false;
    }

    @Override
    public Path normalize()
    {
        return null;
    }

    @Override
    public Path resolve(Path other)
    {
        return null;
    }

    @Override
    public Path resolve(String other)
    {
        String[] others = other.split("/");
        if (others.length == 0)
            others = new String[]{other};

        String[] child = new String[pathSegments.length + others.length];
        System.arraycopy(pathSegments, 0, child, 0, pathSegments.length);
        System.arraycopy(others, 0, child, pathSegments.length, others.length);
        return new NodePath(fileSystem, nodeId, child);
    }

    @Override
    public Path resolveSibling(Path other)
    {
        return null;
    }

    @Override
    public Path resolveSibling(String other)
    {
        return null;
    }

    @Override
    public Path relativize(Path other)
    {
        return null;
    }

    @Override
    public URI toUri()
    {
        return URI.create(fileSystem.provider().getScheme() + ":" + nodeId + "!/" + String.join("/", pathSegments));
    }

    @Override
    public Path toAbsolutePath()
    {
        return this;
    }

    @Override
    public Path toRealPath(LinkOption... options)
    {
        return null;
    }

    @Override
    public File toFile()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Path> iterator()
    {
        return null;
    }

    @Override
    public int compareTo(Path other)
    {
        NodePath otherNodePath = (NodePath)other;
        String otherPath = String.join("/", otherNodePath.pathSegments);
        String thisPath = String.join("/", pathSegments);
        return thisPath.compareTo(otherPath);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        NodePath paths = (NodePath)o;
        return nodeId.equals(paths.nodeId) && Arrays.equals(pathSegments, paths.pathSegments);
    }

    @Override
    public int hashCode()
    {
        int result = Objects.hash(nodeId);
        result = 31 * result + Arrays.hashCode(pathSegments);
        return result;
    }

    @Override
    public String toString()
    {
        return "NodePath{" +
            "nodeId='" + nodeId + '\'' +
            ", path='/" + String.join("/", pathSegments) + '\'' +
            '}';
    }
}
