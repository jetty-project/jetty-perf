package org.eclipse.jetty.perf.ee11;

import java.io.Serializable;
import org.eclipse.jetty.perf.test.ClusteredTestContext;
import org.eclipse.jetty.perf.test.junit.ClusteredTest;
import org.junit.jupiter.api.Test;

public class CometdBenchmark2Test implements Serializable
{
    @Test
    public void testStandard(@ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        CometdClusteredPerfTest.runTest(clusteredTestContext);
    }
}
