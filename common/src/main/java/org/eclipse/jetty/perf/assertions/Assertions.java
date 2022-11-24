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
    public static boolean assertHttpClientStatuses(Path reportRootPath, NodeArrayConfiguration nodeArray, long maxErrors) throws IOException
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

        System.out.println("  " + nodeArray.id() + " errors = " + totalNot200Count + " vs max allowed = " + maxErrors);
        if (totalNot200Count <= maxErrors)
        {
            System.out.println("  OK; value <= " + maxErrors);
            return true;
        }
        else
        {
            System.out.println("  NOK; value > " + maxErrors);
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

        System.out.println("  " + nodeArray.id() + " throughput is " + totalCount + " vs expected " + expectedValue);
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

    public static boolean assertP99Latency(Path reportRootPath, NodeArrayConfiguration nodeArray, long expectedValue, double errorMargin, int toleratedOutliers) throws FileNotFoundException
    {
        // calculate mean p99 value as a basis to eliminate outliers
        long sum = 0L;
        long count = 0L;
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

                    sum += histogram.getValueAtPercentile(99.0);
                    count++;
                }
            }
        }
        long mean = sum / count;

        long trueIntegral = 0L;
        long correctedIntegral = 0L;
        int outliers = 0;
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

                    long valueAtPercentile = histogram.getValueAtPercentile(99.0);
                    trueIntegral += valueAtPercentile;

                    // replace outliers (values over mean * 2) with mean
                    if (valueAtPercentile <= mean * 2)
                    {
                        correctedIntegral += valueAtPercentile;
                    }
                    else
                    {
                        outliers++;
                        correctedIntegral += mean;
                    }
                }
            }
        }
        trueIntegral /= 1_000; // convert ns -> us
        correctedIntegral /= 1_000; // convert ns -> us

        System.out.println("  " + nodeArray.id() + " P99 lat integral is " + trueIntegral + " vs expected " + expectedValue +
            " with " + outliers + " outlier(s), max = " + toleratedOutliers + ", corrected to " + correctedIntegral);
        double error = expectedValue * errorMargin / 100.0;
        double highBound = expectedValue + error;
        double lowBound = expectedValue - error;

        if (trueIntegral >= lowBound && trueIntegral <= highBound)
        {
            System.out.println("  OK; value within " + errorMargin + "% error margin");
            return true;
        }
        else if (outliers <= toleratedOutliers)
        {
            if (correctedIntegral >= lowBound && correctedIntegral <= highBound)
            {
                System.out.println("  OK; corrected value within " + errorMargin + "% error margin");
                return true;
            }
            else
            {
                System.out.println("  NOK; value (even corrected one) out of " + errorMargin + "% error margin");
                return false;
            }
        }
        else
        {
            System.out.println("  NOK; value out of " + errorMargin + "% error margin and has too many outliers");
            return false;
        }
    }
}
