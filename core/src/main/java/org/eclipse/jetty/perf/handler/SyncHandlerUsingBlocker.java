package org.eclipse.jetty.perf.handler;

import java.nio.ByteBuffer;

import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Blocker;
import org.eclipse.jetty.util.Callback;

public class SyncHandlerUsingBlocker extends Handler.Abstract
{
    private final byte[] answer;

    public SyncHandlerUsingBlocker(byte[] answer)
    {
        this.answer = answer;
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception
    {
        Content.Source.consumeAll(request);
        response.setStatus(200);
        Blocker.Shared blocking = new Blocker.Shared();
        try (Blocker.Callback bc = blocking.callback())
        {
            response.write(true, ByteBuffer.wrap(answer), bc);
            bc.block();
        }
        callback.succeeded();
        return true;
    }
}
