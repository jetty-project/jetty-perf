package org.eclipse.jetty.perf.servlet;

import java.io.IOException;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AsyncServlet extends ModernLatencyRecordingServletChannelListener
{
    private final ThreadLocal<byte[]> bufferTl = ThreadLocal.withInitial(() -> new byte[16]);
    private final byte[] answer;

    public AsyncServlet(byte[] answer) throws Exception
    {
        this.answer = answer;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        AsyncContext asyncContext = request.startAsync(request, response);
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
                response.setStatus(500);
                asyncContext.complete();
            }
        });
    }
}
