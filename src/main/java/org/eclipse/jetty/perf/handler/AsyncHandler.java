package org.eclipse.jetty.perf.handler;

import java.nio.ByteBuffer;

import org.eclipse.jetty.core.server.Content;
import org.eclipse.jetty.core.server.Handler;
import org.eclipse.jetty.core.server.Request;
import org.eclipse.jetty.core.server.Response;
import org.eclipse.jetty.util.Callback;

public class AsyncHandler extends Handler.Processor
{
    private final byte[] answer;

    public AsyncHandler(byte[] answer)
    {
        this.answer = answer;
    }

    @Override
    public void process(Request request, Response response, Callback callback)
    {
        Content content = request.readContent();
        if (content == null)
        {
            request.demandContent(() -> process(request, response, callback));
            return;
        }

        try
        {
            if (content.isLast())
            {
                response.setStatus(200);
                response.write(true, callback, ByteBuffer.wrap(answer));
            }
        }
        finally
        {
            content.release();
        }
    }
}
