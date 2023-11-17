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
    private final ByteBuffer answer;

    public SyncHandlerUsingBlocker(byte[] answer)
    {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(answer.length);
        byteBuffer.put(answer);
        byteBuffer.flip();
        this.answer = byteBuffer;
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception
    {
        Content.Source.consumeAll(request);
        response.setStatus(200);
        Content.Sink sink = Response.asBufferedSink(request, response);
        Blocker.Shared blocking = new Blocker.Shared();
        try (Blocker.Callback bc = blocking.callback())
        {
            sink.write(true, answer.asReadOnlyBuffer(), bc);
            bc.block();
        }
        callback.succeeded();
        return true;
    }
}
