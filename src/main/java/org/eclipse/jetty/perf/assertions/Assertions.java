package org.eclipse.jetty.perf.assertions;

import java.nio.file.Path;

import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;

public class Assertions
{
    public static void assertHttpClientStatuses(Path reportRootPath, NodeArrayConfiguration nodeArray, double errorMargin)
    {
        // TODO
    }

    public static void assertThroughput(Path reportRootPath, NodeArrayConfiguration nodeArray, long expectedValue, double errorMargin)
    {
        // TODO
    }

    public static void assertMaxLatency(Path reportRootPath, NodeArrayConfiguration nodeArray, long expectedValue, double errorMargin)
    {
        // TODO
    }
}
