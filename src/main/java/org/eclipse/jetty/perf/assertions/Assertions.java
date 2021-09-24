package org.eclipse.jetty.perf.assertions;

import java.io.FileNotFoundException;
import java.nio.file.Path;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.HistogramLogReader;
import org.mortbay.jetty.orchestrator.configuration.Node;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;

public class Assertions
{
    public static boolean assertHttpClientStatuses(Path reportRootPath, NodeArrayConfiguration nodeArray, double errorMargin)
    {
        // TODO
        return true;
    }

    public static boolean assertThroughput(Path reportRootPath, NodeArrayConfiguration nodeArray, long expectedValue, double errorMargin) throws FileNotFoundException
    {
        Node node = nodeArray.nodes().stream().findFirst().orElseThrow();
        Path perfHlog = reportRootPath.resolve(nodeArray.id()).resolve(node.getId()).resolve("perf.hlog");

        long totalCount = 0L;
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

        System.out.println(nodeArray.id() + " throughput = " + totalCount + " vs expected " + expectedValue);
        double error = expectedValue * errorMargin / 100.0;
        double highBound = expectedValue + error;
        double lowBound = expectedValue - error;
        if (totalCount >= lowBound && totalCount <= highBound)
        {
            System.out.println("OK; value within " + errorMargin + "% error margin");
            return true;
        }
        else
        {
            System.out.println("NOK; value out of " + errorMargin + "% error margin");
            return false;
        }
    }

    public static boolean assertPLatency(Path reportRootPath, NodeArrayConfiguration nodeArray, long expectedValue, double errorMargin, double percentile) throws FileNotFoundException
    {
        Node node = nodeArray.nodes().stream().findFirst().orElseThrow();
        Path perfHlog = reportRootPath.resolve(nodeArray.id()).resolve(node.getId()).resolve("perf.hlog");

        long integral = 0L;
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
        integral /= 1000;

        System.out.println(nodeArray.id() + " p" + percentile + " lat integral = " + integral + " vs expected " + expectedValue);
        double error = expectedValue * errorMargin / 100.0;
        double highBound = expectedValue + error;
        double lowBound = expectedValue - error;

        if (integral >= lowBound && integral <= highBound)
        {
            System.out.println("OK; value within " + errorMargin + "% error margin");
            return true;
        }
        else
        {
            System.out.println("NOK; value out of " + errorMargin + "% error margin");
            return false;
        }
    }
}
