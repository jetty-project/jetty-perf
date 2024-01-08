package org.eclipse.jetty.perf.handler;

import java.io.IOException;

import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;

public class AsyncHandler extends LegacyLatencyRecordingHandlerChannelListener
{
    private final byte[] answer;
    private final ThreadLocal<byte[]> bufferTl = ThreadLocal.withInitial(() -> new byte[16]);

    public AsyncHandler(byte[] answer) throws Exception
    {
        this.answer = answer;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        // super.handle(target, baseRequest, request, response);

        AsyncContext asyncContext = request.startAsync();
        ServletInputStream inputStream = request.getInputStream();
        inputStream.setReadListener(new ReadListener()
        {
            @Override
            public void onDataAvailable() throws IOException
            {
                while (inputStream.isReady())
                {
                    inputStream.read(bufferTl.get());
                }
            }

            @Override
            public void onAllDataRead() throws IOException
            {
                response.setStatus(200);
                response.getOutputStream().write(answer);
                asyncContext.complete();
            }

            @Override
            public void onError(Throwable t)
            {
                asyncContext.complete();
            }
        });

        baseRequest.setHandled(true);
    }
}
