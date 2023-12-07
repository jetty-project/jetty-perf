package org.eclipse.jetty.perf.histogram;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.EncodableHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogReader;
import org.eclipse.jetty.perf.util.PlatformMonitorRecorder;
import org.eclipse.jetty.toolchain.perf.HistogramSnapshot;

public class PlatformMonitorReport
{
    public static void createSnapshotHistogram(Path reportFolder, Path hlogFile) throws IOException
    {
        Path platformMonitorRecorderPath = reportFolder.resolve(PlatformMonitorRecorder.FILENAME);
        String source = loadAsString(platformMonitorRecorderPath);
        if (source == null)
            return;

        Histogram histogram = loadHistogram(hlogFile);
        source = source.replace(PlatformMonitorRecorder.PLACEHOLDER, new HistogramSnapshot(histogram, 32, "Requests", "us", TimeUnit.NANOSECONDS::toMicros).toString());

        try (OutputStream out = Files.newOutputStream(platformMonitorRecorderPath))
        {
            out.write(source.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String loadAsString(Path platformMonitorRecorderPath)
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(platformMonitorRecorderPath), StandardCharsets.UTF_8)))
        {
            StringBuilder sb = new StringBuilder();
            while (true)
            {
                String line = reader.readLine();
                if (line == null)
                    break;

                sb.append(line).append("\n");
            }
            return sb.toString();
        }
        catch (IOException ex)
        {
            return null;
        }
    }

    private static Histogram loadHistogram(Path hlogFile) throws FileNotFoundException
    {
        try (HistogramLogReader histogramLogReader = new HistogramLogReader(hlogFile.toFile()))
        {
            Histogram totalHistogram = new Histogram(3);
            while (histogramLogReader.hasNext())
            {
                EncodableHistogram encodableHistogram = histogramLogReader.nextIntervalHistogram();
                totalHistogram.add((AbstractHistogram)encodableHistogram);
            }
            return totalHistogram;
        }
    }
}
