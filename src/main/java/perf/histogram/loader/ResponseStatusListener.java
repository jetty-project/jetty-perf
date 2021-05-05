package perf.histogram.loader;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

public class ResponseStatusListener implements Resource.NodeListener, LoadGenerator.CompleteListener
{
    private final Timer timer = new Timer();
    // Array of counters:
    //  [0] unknown status [1] HTTP 1xx [2] HTTP 2xx [3] HTTP 3xx [4] HTTP 4xx [5] HTTP 5xx
    private LongAdder[] savedArray = new LongAdder[6];
    private final AtomicReference<LongAdder[]> statuses = new AtomicReference<>(new LongAdder[6]);
    private final PrintWriter printWriter;

    public ResponseStatusListener(String statusFilename) throws IOException
    {
        printWriter = new PrintWriter(statusFilename, StandardCharsets.UTF_8);
        for (int i = 0; i < savedArray.length; i++)
        {
            savedArray[i] = new LongAdder();
            statuses.get()[i] = new LongAdder();
        }
        timer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                writeStatuses();
            }
        }, 1000, 1000);
    }

    private synchronized void writeStatuses()
    {
        LongAdder[] newStatuses = savedArray;
        for (LongAdder newStatus : newStatuses)
        {
            newStatus.reset();
        }
        savedArray = statuses.getAndSet(newStatuses);

        for (int i = 0; i < savedArray.length; i++)
        {
            LongAdder status = savedArray[i];
            printWriter.print(status.sum());
            if (i < savedArray.length - 1)
                printWriter.print(',');
        }
        printWriter.println();
        printWriter.flush();
    }

    @Override
    public void onResourceNode(Resource.Info info)
    {
        int status = info.getStatus();
        int idx = status / 100;
        if (idx > 5 || idx < 1)
            idx = 0;
        statuses.get()[idx].increment();
    }

    @Override
    public void onComplete(LoadGenerator loadGenerator)
    {
        timer.cancel();
        writeStatuses();
        printWriter.close();
    }
}
