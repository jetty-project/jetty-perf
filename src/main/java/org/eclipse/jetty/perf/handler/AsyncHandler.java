package org.eclipse.jetty.perf.handler;

import java.nio.ByteBuffer;

import org.eclipse.jetty.core.server.Content;
import org.eclipse.jetty.core.server.Handler;
import org.eclipse.jetty.core.server.Request;

public class AsyncHandler extends Handler.Abstract
{
    private final byte[] answer;

    public AsyncHandler(byte[] answer)
    {
        this.answer = answer;
    }

    @Override
    public void offer(Request request, Acceptor acceptor) throws Exception
    {
        acceptor.accept(request, this::process);
    }

    private void process(Exchange exchange)
    {
        Content content = exchange.readContent();
        if (content == null)
        {
            exchange.demandContent(() -> process(exchange));
            return;
        }

        try
        {
            if (content.isLast())
            {
                exchange.getResponse().setStatus(200);
                exchange.getResponse().write(true, exchange, ByteBuffer.wrap(answer));
            }
        }
        finally
        {
            content.release();
        }
    }
}
