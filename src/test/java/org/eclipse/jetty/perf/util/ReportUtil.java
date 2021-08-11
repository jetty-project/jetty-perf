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

import org.mortbay.jetty.orchestrator.NodeArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jetty.perf.histogram.HgrmReport;
import org.eclipse.jetty.perf.histogram.HtmlReport;
import org.eclipse.jetty.perf.histogram.JHiccupReport;

public class ReportUtil
{
    private static final Logger LOG = LoggerFactory.getLogger(ReportUtil.class);

    public static void download(NodeArray nodeArray, Path targetFolder, String... filenames) throws IOException
    {
        // Empty string will make this method recursively download everything in the node arrays' CWD.
        List<String> filenamesList = filenames.length == 0 ? Collections.singletonList("") : Arrays.asList(filenames);
        for (String id : nodeArray.ids())
        {
            Path loaderRootPath = nodeArray.rootPathOf(id);
            Path reportFolder = targetFolder.resolve(id);
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

    public static void transformHisto(NodeArray nodeArray, Path targetFolder, String filename) throws IOException
    {
        for (String id : nodeArray.ids())
        {
            Path reportFolder = targetFolder.resolve(id);
            Path hlogFile = reportFolder.resolve(filename);

            try (OutputStream os = new FileOutputStream(new File(reportFolder.toFile(), hlogFile.getFileName() + ".hgrm")))
            {
                HgrmReport.createHgrmHistogram(hlogFile.toFile(), os);
            }
            try (OutputStream os = new FileOutputStream(new File(reportFolder.toFile(), hlogFile.getFileName() + ".html")))
            {
                HtmlReport.createHtmlHistogram(hlogFile.getFileName().toString().split("\\.")[0], hlogFile.toFile(), os);
            }
        }
    }

    public static void xformJHiccup(NodeArray nodeArray, Path targetFolder) throws IOException
    {
        for (String id : nodeArray.ids())
        {
            Path reportFolder = targetFolder.resolve(id);
            Path hlogFile = reportFolder.resolve("jhiccup.hlog");

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
