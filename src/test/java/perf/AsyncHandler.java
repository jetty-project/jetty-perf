package perf;

import java.io.IOException;
import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class AsyncHandler extends AbstractHandler
{
    private final byte[] answer;

    public AsyncHandler(byte[] answer)
    {
        this.answer = answer;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        AsyncContext asyncContext = request.startAsync();
        ServletInputStream inputStream = request.getInputStream();
        byte[] buffer = new byte[16];
        inputStream.setReadListener(new ReadListener()
        {
            @Override
            public void onDataAvailable() throws IOException
            {
                while (inputStream.isReady())
                {
                    inputStream.read(buffer);
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
