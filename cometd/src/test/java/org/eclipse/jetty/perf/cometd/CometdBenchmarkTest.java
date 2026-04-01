package org.eclipse.jetty.perf.cometd;

import java.io.Serializable;
import org.eclipse.jetty.perf.test.ClusteredTestContext;
import org.eclipse.jetty.perf.test.junit.ClusteredTest;
import org.eclipse.jetty.util.thread.Invocable;
import org.junit.jupiter.api.Test;

public class CometdBenchmarkTest implements Serializable
{
    @Test
    public void testEPCAsync(@ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        CometdClusteredPerfTest.runTest(clusteredTestContext, Invocable.InvocationType.BLOCKING);
    }

    @Test
    public void testPCAsync(@ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        CometdClusteredPerfTest.runTest(clusteredTestContext, Invocable.InvocationType.NON_BLOCKING);
    }
}
