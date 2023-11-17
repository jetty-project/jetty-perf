package org.eclipse.jetty.perf.handler;

import java.io.OutputStream;

import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

public class SyncHandlerUsingOutputStream extends Handler.Abstract
{
    private final byte[] answer;

    public SyncHandlerUsingOutputStream(byte[] answer)
    {
        this.answer = answer;
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception
    {
        try (OutputStream outputStream = Response.asBufferedOutputStream(request, response))
        {
            Content.Source.consumeAll(request);
            response.setStatus(200);
            outputStream.write(answer);
            callback.succeeded();
            return true;
        }
    }
}
