package org.eclipse.jetty.perf.histogram.loader;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

public class ResponseStatusListener implements Resource.NodeListener, LoadGenerator.CompleteListener
{
    private final Timer timer = new Timer();

    private final AtomicReference<ConcurrentMap<String, LongAdder>> statuses = new AtomicReference<>(new ConcurrentHashMap<>());
    private final PrintWriter printWriter;
    private final boolean fullStackTrace;
    private int writeCounter;

    public ResponseStatusListener() throws IOException
    {
        this("http-client-statuses.log");
    }

    public ResponseStatusListener(String statusFilename) throws IOException
    {
        this(statusFilename, true);
    }

    public ResponseStatusListener(String statusFilename, boolean fullStackTrace) throws IOException
    {
        this.printWriter = new PrintWriter(statusFilename, StandardCharsets.UTF_8);
        this.fullStackTrace = fullStackTrace;
        this.timer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                writeStatuses();
            }
        }, 1000, 1000);
    }

    private void writeStatuses()
    {
        ConcurrentMap<String, LongAdder> toWrite = statuses.getAndSet(new ConcurrentHashMap<>());
        printWriter.println("[" + (writeCounter++) + "]");
        for (Map.Entry<String, LongAdder> entry : toWrite.entrySet())
        {
            String key = entry.getKey();
            String value = entry.getValue().toString();
            printWriter.print(value);
            printWriter.print('=');
            printWriter.println(key);
        }
        printWriter.println();
        printWriter.flush();
    }

    @Override
    public void onResourceNode(Resource.Info info)
    {
        String key;

        Throwable failure = info.getFailure();
        if (failure != null)
        {
            if (fullStackTrace)
            {
                StringWriter sw = new StringWriter();
                try (PrintWriter pw = new PrintWriter(sw))
                {
                    failure.printStackTrace(pw);
                }
                key = sw.toString();
            }
            else
            {
                key = failure.getClass().getName();
            }
        }
        else
        {
            int status = info.getStatus();
            key = Integer.toString(status);
        }

        LongAdder longAdder = statuses.get().get(key);
        if (longAdder == null)
        {
            statuses.get().compute(key, (k, v) ->
            {
                if (v == null)
                    v = new LongAdder();
                v.increment();
                return v;
            });
        }
        else
        {
            longAdder.increment();
        }
    }

    @Override
    public void onComplete(LoadGenerator loadGenerator)
    {
        timer.cancel();
        writeStatuses();
        printWriter.close();
        writeCounter = 0;
    }
}
