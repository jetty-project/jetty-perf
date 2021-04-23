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

package org.mortbay.jetty.orchestrator.rpc;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.mortbay.jetty.orchestrator.configuration.Jvm;
import org.mortbay.jetty.orchestrator.nodefs.NodeFileSystemProvider;
import org.mortbay.jetty.orchestrator.util.IOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.zeroturnaround.process.PidProcess;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;

public class NodeProcess implements Serializable, AutoCloseable
{
    private static final Logger LOG = LoggerFactory.getLogger(NodeProcess.class);
    public static final String CLASSPATH_FOLDER_NAME = ".classpath";

    private final int pid;

    private NodeProcess(Process process)
    {
        this.pid = Processes.newPidProcess(process).getPid();
    }

    @Override
    public void close()
    {
        try
        {
            PidProcess process = Processes.newPidProcess(pid);
            ProcessUtil.destroyGracefullyOrForcefullyAndWait(process, 10, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    public static void main(String[] args) throws Exception
    {
        String nodeId = args[0];
        String connectString = args[1];
        String readyEchoString = args.length > 2 ? args[2] : null;
        if (readyEchoString != null)
        {
            // This must be the very first printed thing, before any log.
            System.out.print(readyEchoString);
            System.out.flush();
        }

        MDC.put("NodeId", nodeId);
        if (LOG.isDebugEnabled())
            LOG.debug("Starting node [{}] with JVM version '{}' connecting to {}", nodeId, System.getProperty("java.version"), connectString);
        CuratorFramework curator = CuratorFrameworkFactory.newClient(connectString, new RetryNTimes(0, 0));
        curator.start();
        curator.blockUntilConnected();

        if (LOG.isDebugEnabled())
            LOG.debug("Node [{}] connected to {}", nodeId, connectString);
        RpcServer rpcServer = new RpcServer(curator, nodeId);

        Runnable shutdown = () ->
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Node [{}] stopping", nodeId);
            IOUtil.close(rpcServer);
            IOUtil.close(curator);
            if (LOG.isDebugEnabled())
                LOG.debug("Node [{}] stopped", nodeId);
        };
        Runtime.getRuntime().addShutdownHook(new Thread(shutdown));

        rpcServer.run();
        if (LOG.isDebugEnabled())
            LOG.debug("Node [{}] disconnecting from {}", nodeId, connectString);
        shutdown.run();
    }

    public static NodeProcess spawn(Jvm jvm, String hostId, String nodeId, String connectString) throws IOException
    {
        File nodeRootPath = defaultRootPath(nodeId);
        nodeRootPath.mkdirs();

        List<String> cmdLine = buildCommandLine(jvm, defaultLibPath(hostId), nodeId, connectString);
        return new NodeProcess(new ProcessBuilder(cmdLine)
            .directory(nodeRootPath)
            .inheritIO()
            .start());
    }

    public static Thread spawnThread(String nodeId, String connectString)
    {
        File nodeRootPath = defaultRootPath(nodeId);
        nodeRootPath.mkdirs();

        Thread t = new Thread(() ->
        {
            try
            {
                NodeProcess.main(new String[]{nodeId, connectString});
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });
        t.start();
        return t;
    }

    private static File defaultRootPath(String hostId)
    {
        return new File(System.getProperty("user.home") + "/." + NodeFileSystemProvider.PREFIX + "/" + hostId);
    }

    private static File defaultLibPath(String hostId)
    {
        File rootPath = defaultRootPath(hostId);
        return new File(rootPath, CLASSPATH_FOLDER_NAME);
    }

    private static List<String> buildCommandLine(Jvm jvm, File libPath, String nodeId, String connectString)
    {
        List<String> cmdLine = new ArrayList<>();
        cmdLine.add(jvm.executable());
        cmdLine.addAll(jvm.getOpts());
        cmdLine.add("-classpath");
        cmdLine.add(buildClassPath(libPath));
        cmdLine.add(NodeProcess.class.getName());
        cmdLine.add(nodeId);
        cmdLine.add(connectString);
        return cmdLine;
    }

    private static String buildClassPath(File libPath)
    {
        File[] entries = libPath.listFiles();
        StringBuilder sb = new StringBuilder();
        for (File entry : entries)
        {
            sb.append(entry.getPath()).append(File.pathSeparatorChar);
        }
        if (sb.length() > 0)
            sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }
}
