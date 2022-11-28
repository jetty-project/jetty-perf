package org.eclipse.jetty.perf.ee10;

import java.io.IOException;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AsyncEE10Servlet extends HttpServlet
{
    private final byte[] answer;

    public AsyncEE10Servlet(byte[] answer)
    {
        this.answer = answer;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        AsyncContext asyncContext = request.startAsync(request, response);
        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.setWriteListener(new WriteListener() {
            @Override
            public void onWritePossible() throws IOException
            {
                outputStream.write(answer);
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