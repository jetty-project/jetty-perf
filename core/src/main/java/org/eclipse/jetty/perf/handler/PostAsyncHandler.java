package org.eclipse.jetty.perf.handler;

import java.nio.ByteBuffer;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

public class PostAsyncHandler extends Handler.Abstract
{
    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(1024 * 1024);

    public PostAsyncHandler(InvocationType invocationType)
    {
        super(invocationType);
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback)
    {
        request.demand(new Task.Abstract(getInvocationType()) {
            @Override
            public void run()
            {
                Content.Chunk chunk = request.read();
                if (chunk == null)
                {
                    request.demand(this);
                    return;
                }
                if (Content.Chunk.isFailure(chunk))
                {
                    callback.failed(chunk.getFailure());
                    return;
                }
                chunk.release();
                if (!chunk.isLast())
                {
                    request.demand(this);
                    return;
                }

                long responseLength = request.getHeaders().getLongField("JLG-Response-Length");
                if (responseLength > 0)
                {
                    if (responseLength > DATA.remaining())
                        callback.failed(new Exception("Requested response length (JLG-Response-Length header) too large"));
                    else
                        response.write(true, DATA.slice(0, (int)responseLength), callback);
                }
            }
        });
        return true;
    }
}
