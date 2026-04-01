package org.eclipse.jetty.perf.handler;

import org.eclipse.jetty.perf.test.ClusteredTestContext;
import org.eclipse.jetty.perf.test.Jetty12ClusteredPerfTest;
import org.eclipse.jetty.perf.test.junit.ClusteredTest;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.thread.Invocable;
import org.junit.jupiter.api.Test;

public class CoreHandlerPostPerfTest
{
    @Test
    public void testEPCAsyncPost(@ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        Jetty12ClusteredPerfTest.runTest(clusteredTestContext, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection(false);
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            PostAsyncHandler asyncHandler = new PostAsyncHandler(Invocable.InvocationType.BLOCKING);
            targetContextHandler.setHandler(asyncHandler);
            return contextHandlerCollection;
        });
    }
    @Test
    public void testPCAsyncPost(@ClusteredTest ClusteredTestContext clusteredTestContext) throws Exception
    {
        Jetty12ClusteredPerfTest.runTest(clusteredTestContext, () ->
        {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection(false);
            ContextHandler targetContextHandler = new ContextHandler("/");
            contextHandlerCollection.addHandler(targetContextHandler);
            ContextHandler uselessContextHandler = new ContextHandler("/useless");
            contextHandlerCollection.addHandler(uselessContextHandler);
            PostAsyncHandler asyncHandler = new PostAsyncHandler(Invocable.InvocationType.NON_BLOCKING);
            targetContextHandler.setHandler(asyncHandler);
            return contextHandlerCollection;
        });
    }
}
