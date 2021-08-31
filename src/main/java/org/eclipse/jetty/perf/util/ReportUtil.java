package org.eclipse.jetty.perf.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jetty.perf.histogram.HgrmReport;
import org.eclipse.jetty.perf.histogram.JHiccupReport;
import org.eclipse.jetty.perf.histogram.PerfReport;
import org.mortbay.jetty.orchestrator.Cluster;
import org.mortbay.jetty.orchestrator.NodeArray;
import org.mortbay.jetty.orchestrator.configuration.ClusterConfiguration;
import org.mortbay.jetty.orchestrator.configuration.NodeArrayConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportUtil
{
    private static final Logger LOG = LoggerFactory.getLogger(ReportUtil.class);

    public static void generateReport(Path reportPath, ClusterConfiguration clusterConfiguration, Cluster cluster) throws IOException
    {
        for (String nodeArrayId : clusterConfiguration.nodeArrays().stream().map(NodeArrayConfiguration::id).collect(Collectors.toList()))
        {
            NodeArray nodeArray = cluster.nodeArray(nodeArrayId);
            Path targetPath = reportPath.resolve(nodeArrayId);
            download(nodeArray, targetPath);
            transformPerfHisto(nodeArray, targetPath);
            transformJHiccupHisto(nodeArray, targetPath);
        }
    }

    public static void download(NodeArray nodeArray, Path targetFolder, String... filenames) throws IOException
    {
        // Empty string will make this method recursively download everything in the node arrays' CWD.
        List<String> filenamesList = filenames.length == 0 ? Collections.singletonList("") : Arrays.asList(filenames);
        for (String id : nodeArray.ids())
        {
            Path loaderRootPath = nodeArray.rootPathOf(id);
            Path reportFolder = targetFolder.resolve(id + "-" + nodeArray.hostnameOf(id));
            download(loaderRootPath, reportFolder, filenamesList);
        }
    }

    private static void download(Path source, Path destinationDir, List<String> filenames) throws IOException
    {
        Files.createDirectories(destinationDir);
        for (String filename : filenames)
        {
            Path resolvedSourcePath = source.resolve(filename);
            if (Files.isDirectory(resolvedSourcePath))
            {
                Files.walk(resolvedSourcePath).forEach(path ->
                {
                    try
                    {
                        Path targetPath = destinationDir.resolve(resolvedSourcePath.relativize(path).toString());
                        Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    catch (IOException e)
                    {
                        LOG.error("Error downloading", e);
                    }
                });
            }
            else
            {
                Files.copy(resolvedSourcePath, destinationDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static void transformPerfHisto(NodeArray nodeArray, Path targetFolder) throws IOException
    {
        transformPerfHisto(nodeArray, targetFolder, "perf.hlog");
    }

    public static void transformPerfHisto(NodeArray nodeArray, Path targetFolder, String filename) throws IOException
    {
        for (String id : nodeArray.ids())
        {
            Path reportFolder = targetFolder.resolve(id + "-" + nodeArray.hostnameOf(id));
            Path hlogFile = reportFolder.resolve(filename);

            try (OutputStream os = new FileOutputStream(new File(reportFolder.toFile(), hlogFile.getFileName() + ".hgrm")))
            {
                HgrmReport.createHgrmHistogram(hlogFile.toFile(), os);
            }
            try (OutputStream os = new FileOutputStream(new File(reportFolder.toFile(), hlogFile.getFileName() + ".html")))
            {
                PerfReport.createHtmlHistogram(hlogFile.getFileName().toString().split("\\.")[0], hlogFile.toFile(), os);
            }
        }
    }

    public static void transformJHiccupHisto(NodeArray nodeArray, Path targetFolder) throws IOException
    {
        for (String id : nodeArray.ids())
        {
            Path reportFolder = targetFolder.resolve(id + "-" + nodeArray.hostnameOf(id));
            Path hlogFile = reportFolder.resolve("jhiccup.hlog");
            if (!Files.exists(hlogFile))
                continue;

            try (OutputStream os = new FileOutputStream(new File(reportFolder.toFile(), hlogFile.getFileName() + ".hgrm")))
            {
                HgrmReport.createHgrmHistogram(hlogFile.toFile(), os);
            }
            try (OutputStream os = new FileOutputStream(new File(reportFolder.toFile(), hlogFile.getFileName() + ".html")))
            {
                JHiccupReport.createHtmlHistogram(hlogFile.toFile(), os);
            }
        }
    }
}
