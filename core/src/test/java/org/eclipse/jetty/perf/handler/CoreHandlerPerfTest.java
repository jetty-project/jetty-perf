package org.eclipse.jetty.perf.handler;

import java.time.Duration;

import org.eclipse.jetty.perf.test.FlatPerfTest;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class CoreHandlerPerfTest
{
    private static final Duration WARMUP_DURATION = Duration.ofSeconds(60);
    private static final Duration RUN_DURATION = Duration.ofSeconds(180);

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
        FlatPerfTest.runTest(testName, () ->
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
        FlatPerfTest.runTest(testName, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection(false);
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            AsyncHandler asyncHandler = new AsyncHandler("Hi there!".getBytes(US_ASCII));
            targetContextHandler.setHandler(asyncHandler);
            return contextHandlerCollection;
        });
    }
}
