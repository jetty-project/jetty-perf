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
        new Process(request, response).run();
    }

    class Process implements Runnable
    {
        private final Request _request;
        private final Response _response;

        Process(Request request, Response response)
        {
            _request = request;
            _response = response;
        }

        @Override
        public void run()
        {
            while (true)
            {
                Content content = _request.readContent();
                if (content == null)
                {
                    _request.demandContent(this);
                    return;
                }
                content.release();
                if (content.isLast())
                {
                    _response.setStatus(200);
                    _response.write(true, _response.getCallback(), ByteBuffer.wrap(answer));
                }
            }
        }
    }
}
