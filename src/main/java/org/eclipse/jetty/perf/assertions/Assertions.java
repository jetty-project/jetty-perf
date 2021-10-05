package org.eclipse.jetty.perf.assertions;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.HistogramLogReader;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;

public class Assertions
{
    public static boolean assertHttpClientStatuses(Path reportRootPath, NodeArrayConfiguration nodeArray, long expectedValue, double errorMargin) throws IOException
    {
        List<Map.Entry<Long, String>> counters = new ArrayList<>();
        long totalNot200Count = 0L;
        for (Node node : nodeArray.nodes())
        {
            Path statusesLog = reportRootPath.resolve(nodeArray.id()).resolve(node.getId()).resolve("http-client-statuses.log");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(statusesLog.toFile()), StandardCharsets.UTF_8)))
            {
                while (true)
                {
                    String line = reader.readLine();
                    if (line == null)
                        break;

                    int idx = line.indexOf('=');
                    if (idx == -1)
                        continue;

                    long count = Long.parseLong(line.substring(0, idx));
                    String status = line.substring(idx + 1);

                    if (!"200".equals(status))
                    {
                        totalNot200Count += count;
                        counters.add(new AbstractMap.SimpleImmutableEntry<>(count, status));
                    }
                }
            }
        }

        double maxErrors = expectedValue * errorMargin / 100.0;
        if (totalNot200Count < maxErrors)
        {
            System.out.println("  OK; value within " + errorMargin + "% error margin (" + totalNot200Count + " error(s))");
            return true;
        }
        else
        {
            System.out.println("  NOK; value out of " + errorMargin + "% error margin (" + totalNot200Count + " errors)");
            for (Map.Entry<Long, String> entry : counters)
            {
                System.out.printf("   %5d %s\n", entry.getKey(), entry.getValue());
            }
            return false;
        }
    }

    public static boolean assertThroughput(Path reportRootPath, NodeArrayConfiguration nodeArray, long expectedValue, double errorMargin) throws FileNotFoundException
    {
        long totalCount = 0L;
        for (Node node : nodeArray.nodes())
        {
            Path perfHlog = reportRootPath.resolve(nodeArray.id()).resolve(node.getId()).resolve("perf.hlog");
            try (HistogramLogReader histogramLogReader = new HistogramLogReader(perfHlog.toFile()))
            {
                while (true)
                {
                    AbstractHistogram histogram = (AbstractHistogram)histogramLogReader.nextIntervalHistogram();
                    if (histogram == null)
                        break;

                    totalCount += histogram.getTotalCount();
                }
            }
        }

        System.out.println("  " + nodeArray.id() + " throughput = " + totalCount + " vs expected " + expectedValue);
        double error = expectedValue * errorMargin / 100.0;
        double highBound = expectedValue + error;
        double lowBound = expectedValue - error;
        if (totalCount >= lowBound && totalCount <= highBound)
        {
            System.out.println("  OK; value within " + errorMargin + "% error margin");
            return true;
        }
        else
        {
            System.out.println("  NOK; value out of " + errorMargin + "% error margin");
            return false;
        }
    }

    public static boolean assertPLatency(Path reportRootPath, NodeArrayConfiguration nodeArray, long expectedValue, double errorMargin, double percentile) throws FileNotFoundException
    {
        long integral = 0L;
        for (Node node : nodeArray.nodes())
        {
            Path perfHlog = reportRootPath.resolve(nodeArray.id()).resolve(node.getId()).resolve("perf.hlog");
            try (HistogramLogReader histogramLogReader = new HistogramLogReader(perfHlog.toFile()))
            {
                while (true)
                {
                    AbstractHistogram histogram = (AbstractHistogram)histogramLogReader.nextIntervalHistogram();
                    if (histogram == null)
                        break;

                    integral += histogram.getValueAtPercentile(percentile);
                }
            }
        }
        integral /= 1_000; // convert ns -> us

        System.out.println("  " + nodeArray.id() + " p" + percentile + " lat integral = " + integral + " vs expected " + expectedValue);
        double error = expectedValue * errorMargin / 100.0;
        double highBound = expectedValue + error;
        double lowBound = expectedValue - error;

        if (integral >= lowBound && integral <= highBound)
        {
            System.out.println("  OK; value within " + errorMargin + "% error margin");
            return true;
        }
        else
        {
            System.out.println("  NOK; value out of " + errorMargin + "% error margin");
            return false;
        }
    }
}
