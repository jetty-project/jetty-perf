package org.eclipse.jetty.perf.servlet;

import java.io.IOException;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SyncServlet extends ModernLatencyRecordingServletChannelListener
{
    private final ThreadLocal<byte[]> bufferTl = ThreadLocal.withInitial(() -> new byte[16]);
    private final byte[] answer;

    public SyncServlet(byte[] answer) throws Exception
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
