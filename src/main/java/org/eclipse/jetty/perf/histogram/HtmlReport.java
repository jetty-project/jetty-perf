package org.eclipse.jetty.perf.histogram;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class HtmlReport
{
    public static void createHtmlHistogram(String title, File hlogFile, OutputStream out) throws IOException
    {
        String html = loadAsString(HtmlReport.class.getResourceAsStream(HtmlReport.class.getSimpleName() + ".html"));
        String histograms = loadAsString(new FileInputStream(hlogFile));

        html = html.replace("##TITLE##", title);
        html = html.replace("##HISTOGRAMS##", histograms);

        out.write(html.getBytes(StandardCharsets.UTF_8));
    }

    private static String loadAsString(InputStream input) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)))
        {
            while (true)
            {
                String line = reader.readLine();
                if (line == null)
                    break;

                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
