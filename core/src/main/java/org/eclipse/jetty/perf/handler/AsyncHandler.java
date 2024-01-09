package org.eclipse.jetty.perf.handler;

import java.nio.ByteBuffer;

import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

public class AsyncHandler extends Handler.Abstract
{
    private final ByteBuffer answer;

    public AsyncHandler(byte[] answer)
    {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(answer.length);
        byteBuffer.put(answer);
        byteBuffer.flip();
        this.answer = byteBuffer;
    }

    @Override
    public boolean process(Request request, Response response, Callback callback)
    {
        Content.Source.consumeAll(request, new Callback.Nested(callback)
        {
            @Override
            public void succeeded()
            {
                response.setStatus(200);
                response.write(true, answer.asReadOnlyBuffer(), getCallback());
            }
        });
        return true;
    }
}
