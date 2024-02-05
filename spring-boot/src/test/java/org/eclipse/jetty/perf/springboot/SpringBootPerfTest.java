package org.eclipse.jetty.perf.springboot;

import org.eclipse.jetty.perf.test.ClusteredTestContext;
import org.eclipse.jetty.perf.test.junit.ClusteredTest;
import org.junit.jupiter.api.Test;

public class SpringBootPerfTest
{
    @Test
    public void testSimple(@ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        SpringBootClusteredPerfTest.runTest(clusteredTestContext);
    }
}
