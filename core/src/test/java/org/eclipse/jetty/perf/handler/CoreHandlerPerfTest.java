package org.eclipse.jetty.perf.handler;

import org.eclipse.jetty.perf.test.ClusteredPerfTest;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class CoreHandlerPerfTest
{
    private String testName;

    @BeforeEach
    protected void beforeEach(TestInfo testInfo)
    {
        // Generate test name
        String className = testInfo.getTestClass().orElseThrow().getName();
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        String methodName = testInfo.getTestMethod().orElseThrow().getName();
        testName = simpleClassName + "_" + methodName;
    }

    @Test
    public void testNoGzipAsync() throws Exception
    {
        ClusteredPerfTest.runTest(testName, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            AsyncHandler asyncHandler = new AsyncHandler("Hi there!".getBytes(US_ASCII));
            targetContextHandler.setHandler(asyncHandler);
            return contextHandlerCollection;
        });
    }

    @Test
    public void testNoGzipFullyAsyncHandlerTree() throws Exception
    {
        ClusteredPerfTest.runTest(testName, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection(false);
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            AsyncHandler asyncHandler = new AsyncHandler("Hi there!".getBytes(US_ASCII));
            targetContextHandler.setHandler(asyncHandler);
            return contextHandlerCollection;
        }, p ->
        {
            if (p.SERVER_SELECTOR_COUNT == -1)
                p.SERVER_SELECTOR_COUNT = Runtime.getRuntime().availableProcessors();
        });
    }
}
