package org.eclipse.jetty.perf.util;

import org.eclipse.jetty.perf.test.PerfTestParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JenkinsParameters
{
    private static final Logger LOG = LoggerFactory.getLogger(PerfTestParams.class);

    public static String read(String name, String defaultValue)
    {
        String env = System.getenv(name);
        if (env == null)
            return defaultValue;
        return env.trim();
    }

    public static int readAsInt(String name, int defaultValue)
    {
        String env = System.getenv(name);
        if (env == null)
            return defaultValue;
        try
        {
            return Integer.parseInt(env.trim());
        }
        catch (NumberFormatException e)
        {
            LOG.warn("Jenkins parameter '{}={}' cannot be parsed as int, defaulting to {}", name, env, defaultValue);
            return defaultValue;
        }
    }
}
