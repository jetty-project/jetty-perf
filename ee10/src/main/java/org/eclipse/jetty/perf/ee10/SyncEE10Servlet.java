package org.eclipse.jetty.perf.ee10;

import java.io.IOException;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SyncEE10Servlet extends HttpServlet
{
    private final ThreadLocal<byte[]> bufferTl = ThreadLocal.withInitial(() -> new byte[16]);
    private final byte[] answer;

    public SyncEE10Servlet(byte[] answer)
    {
        this.answer = answer;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        ServletInputStream inputStream = request.getInputStream();
        while (true)
        {
            int read = inputStream.read(bufferTl.get());
            if (read == -1)
                break;
        }
        response.setStatus(200);
        response.getOutputStream().write(answer);
    }
}
