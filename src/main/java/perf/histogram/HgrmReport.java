package perf.histogram;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogReader;

public class HgrmReport
{
    public static void createHgrmHistogram(File hlogFile, OutputStream out) throws FileNotFoundException
    {
        try (HistogramLogReader reader = new HistogramLogReader(hlogFile))
        {
            Histogram total = new Histogram(3);
            while (reader.hasNext())
            {
                Histogram histogram = (Histogram) reader.nextIntervalHistogram();
                total.add(histogram);
            }
            PrintStream ps = new PrintStream(out);
            total.outputPercentileDistribution(ps, 1000.0); // scale by 1000 to report in microseconds
            ps.flush();
        }
    }
}
