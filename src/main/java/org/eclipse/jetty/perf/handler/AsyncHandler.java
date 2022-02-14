package org.eclipse.jetty.perf.handler;

import java.nio.ByteBuffer;

import org.eclipse.jetty.core.server.Content;
import org.eclipse.jetty.core.server.Handler;
import org.eclipse.jetty.core.server.Request;
import org.eclipse.jetty.core.server.Response;

public class AsyncHandler extends Handler.Abstract
{
    private final byte[] answer;

    public AsyncHandler(byte[] answer)
    {
        this.answer = answer;
    }

    @Override
    public void handle(Request request) throws Exception
    {
        Response response = request.accept();
        process(request, response);
    }

    private void process(Request request, Response response)
    {
        Content content = request.readContent();
        if (content == null)
        {
            request.demandContent(() -> process(request, response));
            return;
        }

        try
        {
            if (content.isLast())
            {
                response.setStatus(200);
                response.write(true, response.getCallback(), ByteBuffer.wrap(answer));
            }
        }
        finally
        {
            content.release();
        }
    }
}
