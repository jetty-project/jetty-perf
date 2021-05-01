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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.Channel;
import net.schmizz.sshj.connection.channel.direct.DirectConnection;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Signal;
import net.schmizz.sshj.connection.channel.forwarded.ConnectListener;
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.LocalSourceFile;
import org.mortbay.jetty.orchestrator.nodefs.NodeFileSystemProvider;
import org.mortbay.jetty.orchestrator.rpc.GlobalNodeId;
import org.mortbay.jetty.orchestrator.rpc.NodeProcess;
import org.mortbay.jetty.orchestrator.util.ByteBuilder;
import org.mortbay.jetty.orchestrator.util.IOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SshRemoteHostLauncher implements HostLauncher, JvmDependent
{
    private static final Logger LOG = LoggerFactory.getLogger(SshRemoteHostLauncher.class);

    private final Map<String, RemoteNodeHolder> nodes = new HashMap<>();
    private final String username;
    private Jvm jvm;

    public SshRemoteHostLauncher()
    {
        this(System.getProperty("user.name"));
    }

    public SshRemoteHostLauncher(String username)
    {
        this.username = username;
    }

    @Override
    public void close()
    {
        nodes.values().forEach(IOUtil::close);
        nodes.clear();
    }

    @Override
    public Jvm jvm()
    {
        return jvm;
    }

    @Override
    public SshRemoteHostLauncher jvm(Jvm jvm)
    {
        this.jvm = jvm;
        return this;
    }

    @Override
    public String launch(GlobalNodeId globalNodeId, String connectString) throws Exception
    {
        GlobalNodeId nodeId = globalNodeId.getHostGlobalId();
        if (!nodeId.equals(globalNodeId))
            throw new IllegalArgumentException("node id is not the one of a host node");
        if (nodes.containsKey(nodeId.getHostname()))
            throw new IllegalArgumentException("ssh launcher already launched node on host " + nodeId.getHostname());

        FileSystem fileSystem = null;
        SSHClient sshClient = null;
        SocketForwardingConnectListener forwardingConnectListener = null;
        AutoCloseable forwarding = null;
        Session.Command cmd = null;
        Session session = null;
        try
        {
            sshClient = new SSHClient();
            sshClient.addHostKeyVerifier(new PromiscuousVerifier()); // or loadKnownHosts() instead?

            boolean useTunnel = System.getProperty("connect.via.hostname") != null;

            if (useTunnel)
            {
                SSHClient first = new SSHClient();
                first.addHostKeyVerifier(new PromiscuousVerifier());
                first.connect(System.getProperty("connect.via.hostname"), Integer.getInteger("connect.via.port"));
                first.authPublickey(System.getProperty("connect.via.user"));
                DirectConnection tunnel = first.newDirectConnection(System.getProperty("connect.via.hostname"),
                                                                     Integer.getInteger("connect.via.port"));

                sshClient.connectVia(tunnel, nodeId.getHostname(), 22);
                // public key auth
                sshClient.authPublickey(System.getProperty("connect.via.user"));
            }
            else
            {
                sshClient.connect(nodeId.getHostname());
                sshClient.authPublickey(username);
            }

            // do remote port forwarding
            int zkPort = Integer.parseInt(connectString.split(":")[1]);
            forwardingConnectListener = new SocketForwardingConnectListener(nodeId.getHostname(), new InetSocketAddress("localhost", zkPort));
            RemotePortForwarder.Forward forward = sshClient.getRemotePortForwarder().bind(
                new RemotePortForwarder.Forward(0), // remote port, dynamically choose one
                forwardingConnectListener
            );
            SSHClient finalSshClient = sshClient;
            forwarding = () -> finalSshClient.getRemotePortForwarder().cancel(forward);
            String remoteConnectString = "localhost:" + forward.getPort();

            List<String> remoteClasspathEntries = new ArrayList<>();
            String[] classpathEntries = System.getProperty("java.class.path").split(File.pathSeparator);
            try (SFTPClient sftpClient = sshClient.newStatefulSFTPClient())
            {
                for (String classpathEntry : classpathEntries)
                {
                    File cpFile = new File(classpathEntry);
                    remoteClasspathEntries.add("." + NodeFileSystemProvider.PREFIX + "/" + nodeId.getHostId() + "/" + NodeProcess.CLASSPATH_FOLDER_NAME + "/" + cpFile.getName());
                    if (cpFile.isDirectory())
                        copyDir(sftpClient, nodeId.getHostId(), cpFile, 1);
                    else
                        copyFile(sftpClient, nodeId.getHostId(), cpFile.getName(), new FileSystemFile(cpFile));
                }
            }
            String remoteClasspath = String.join(":", remoteClasspathEntries);
            String cmdLine = String.join(" ", buildCommandLine(jvm, remoteClasspath, nodeId.getHostId(), remoteConnectString));

            session = sshClient.startSession();
            session.allocateDefaultPTY();
            cmd = session.exec(cmdLine);

            new StreamCopier(cmd.getInputStream(), new LineBufferingOutputStream(System.out)).spawnDaemon(nodeId.getHostname() + "-stdout");
            new StreamCopier(cmd.getErrorStream(), new LineBufferingOutputStream(System.err)).spawnDaemon(nodeId.getHostname() + "-stderr");

            HashMap<String, Object> env = new HashMap<>();
            env.put(SFTPClient.class.getName(), sshClient.newStatefulSFTPClient());
            fileSystem = FileSystems.newFileSystem(URI.create(NodeFileSystemProvider.PREFIX + ":" + nodeId.getHostId()), env);

            RemoteNodeHolder remoteNodeHolder = new RemoteNodeHolder(nodeId, fileSystem, sshClient, forwardingConnectListener, forwarding, session, cmd);
            nodes.put(nodeId.getHostname(), remoteNodeHolder);
            return remoteConnectString;
        }
        catch (Exception e)
        {
            IOUtil.close(fileSystem, cmd, session, forwardingConnectListener, forwarding, sshClient);
            throw new Exception("Error launching host '" + nodeId.getHostname() + "'", e);
        }
    }

    private static List<String> buildCommandLine(Jvm jvm, String remoteClasspath, String nodeId, String connectString)
    {
        List<String> cmdLine = new ArrayList<>();
        cmdLine.add(jvm.executable());
        cmdLine.addAll(jvm.getOpts());
        cmdLine.add("-classpath");
        cmdLine.add("\"" + remoteClasspath + "\"");
        cmdLine.add(NodeProcess.class.getName());
        cmdLine.add("\"" + nodeId + "\"");
        cmdLine.add("\"" + connectString + "\"");
        return cmdLine;
    }

    private void copyFile(SFTPClient sftpClient, String hostId, String filename, LocalSourceFile localSourceFile) throws Exception
    {
        String destFilename = "." + NodeFileSystemProvider.PREFIX + "/" + hostId + "/" + NodeProcess.CLASSPATH_FOLDER_NAME + "/" + filename;
        String parentFilename = destFilename.substring(0, destFilename.lastIndexOf('/'));

        sftpClient.mkdirs(parentFilename);
        // do not transfer file if already there, same size and same mtime
        FileAttributes fileAttributes = sftpClient.statExistence(destFilename);
        if (fileAttributes != null && fileAttributes.getSize() == localSourceFile.getLength() &&
            fileAttributes.getMtime() != localSourceFile.getLastModifiedTime())
        {
            LOG.info("could skip transfering file {}", localSourceFile.getName());
        }
        sftpClient.put(localSourceFile, destFilename);
    }

    private void copyDir(SFTPClient sftpClient, String hostId, File cpFile, int depth) throws Exception
    {
        File[] files = cpFile.listFiles();
        if (files == null)
            return;

        for (File file : files)
        {
            if (file.isDirectory())
            {
                copyDir(sftpClient, hostId, file, depth + 1);
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
                copyFile(sftpClient, hostId, filename, new FileSystemFile(file));
            }
        }
    }

    private static class RemoteNodeHolder implements AutoCloseable {
        private final GlobalNodeId nodeId;
        private final FileSystem fileSystem;
        private final SSHClient sshClient;
        private final SocketForwardingConnectListener forwardingConnectListener;
        private final AutoCloseable forwarding;
        private final Session session;
        private final Session.Command command;

        private RemoteNodeHolder(GlobalNodeId nodeId, FileSystem fileSystem, SSHClient sshClient, SocketForwardingConnectListener forwardingConnectListener, AutoCloseable forwarding, Session session, Session.Command command) {
            this.nodeId = nodeId;
            this.fileSystem = fileSystem;
            this.sshClient = sshClient;
            this.forwardingConnectListener = forwardingConnectListener;
            this.forwarding = forwarding;
            this.session = session;
            this.command = command;
        }

        @Override
        public void close() throws Exception
        {
            IOUtil.close(fileSystem);

            // 0x03 is the character for CTRL-C -> send it to the remote PTY
            session.getOutputStream().write(0x03);
            // also send TERM signal
            command.signal(Signal.TERM);
            try
            {
                command.join(10, TimeUnit.SECONDS);
            }
            catch (Exception e)
            {
                // timeout? error? too late, try to kill the process
                command.signal(Signal.KILL);
            }
            IOUtil.close(command);
            IOUtil.close(session);
            try (SFTPClient sftpClient = sshClient.newStatefulSFTPClient())
            {
                deltree(sftpClient, "." + NodeFileSystemProvider.PREFIX + "/" + nodeId.getClusterId());
            }
            IOUtil.close(forwardingConnectListener);
            IOUtil.close(forwarding);
            IOUtil.close(sshClient);
        }

        private static void deltree(SFTPClient sftpClient, String path) throws IOException
        {
            List<RemoteResourceInfo> ls = sftpClient.ls(path);
            for (RemoteResourceInfo l : ls)
            {
                if (l.isDirectory())
                    deltree(sftpClient, l.getPath());
                else
                    sftpClient.rm(l.getPath());
            }
            sftpClient.rmdir(path);
        }
    }

    private static class LineBufferingOutputStream extends OutputStream
    {
        private final OutputStream delegate;
        private final ByteBuilder byteBuilder = new ByteBuilder(256);

        public LineBufferingOutputStream(OutputStream delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException
        {
            if (byteBuilder.isFull())
            {
                delegate.write(byteBuilder.getBuffer());
                delegate.flush();
                byteBuilder.clear();
            }
            byteBuilder.append(b);
            if (b == '\n' || b == '\r')
            {
                delegate.write(byteBuilder.getBuffer(), 0, byteBuilder.length());
                delegate.flush();
                byteBuilder.clear();
            }
        }

        @Override
        public void close() throws IOException
        {
            delegate.close();
        }
    }

    private static class StreamCopier
    {
        private final InputStream is;
        private final OutputStream os;
        private final int bufferSize;

        private StreamCopier(InputStream is, OutputStream os)
        {
            this(is, os, 1);
        }

        private StreamCopier(InputStream is, OutputStream os, int bufferSize)
        {
            this.is = is;
            this.os = os;
            this.bufferSize = bufferSize;
        }

        public void spawnDaemon(String name)
        {
            Thread thread = new Thread(() ->
            {
                try
                {
                    IOUtil.copy(is, os, bufferSize, true);
                }
                catch (Exception e)
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("Error copying stream", e);
                }
            }, name);
            thread.setDaemon(true);
            thread.start();
        }
    }

    private static class SocketForwardingConnectListener implements ConnectListener, AutoCloseable
    {
        private final String threadNamePrefix;
        private final SocketAddress addr;
        private Socket socket;
        private Channel.Forwarded channel;

        private SocketForwardingConnectListener(String threadNamePrefix, SocketAddress addr)
        {
            this.threadNamePrefix = threadNamePrefix;
            this.addr = addr;
        }

        @Override
        public void close()
        {
            IOUtil.close(channel, socket);
        }

        @Override
        public void gotConnect(Channel.Forwarded channel) throws IOException
        {
            this.channel = channel;
            socket = new Socket();
            socket.setSendBufferSize(channel.getLocalMaxPacketSize());
            socket.setReceiveBufferSize(channel.getRemoteMaxPacketSize());
            socket.connect(addr);

            channel.confirm();

            new SshRemoteHostLauncher.StreamCopier(socket.getInputStream(), channel.getOutputStream(), channel.getRemoteMaxPacketSize())
                .spawnDaemon(threadNamePrefix + "-soc2chan");

            new SshRemoteHostLauncher.StreamCopier(channel.getInputStream(), socket.getOutputStream(), channel.getLocalMaxPacketSize())
                .spawnDaemon(threadNamePrefix + "-chan2soc");
        }
    }
}
