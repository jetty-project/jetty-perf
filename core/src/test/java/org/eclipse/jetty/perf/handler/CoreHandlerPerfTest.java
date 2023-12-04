package org.eclipse.jetty.perf.handler;

import org.eclipse.jetty.perf.test.ClusteredPerfTest;
import org.eclipse.jetty.perf.test.ClusteredTestContext;
import org.eclipse.jetty.perf.test.junit.ClusteredTest;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class CoreHandlerPerfTest
{
    @Test
    public void testNoGzipAsync(@ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        ClusteredPerfTest.runTest(clusteredTestContext, () ->
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
    public void testNoGzipFullyAsyncHandlerTree(@ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        ClusteredPerfTest.runTest(clusteredTestContext, () ->
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
