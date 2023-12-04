package org.eclipse.jetty.perf.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.perf.test.PerfTestParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JenkinsParameters implements Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(PerfTestParams.class);

    private final Map<String, String> environment = new HashMap<>(System.getenv());

    public String read(String name, String defaultValue)
    {
        String env = environment.get(name);
        if (env == null)
            return defaultValue;
        return env.trim();
    }

    public int readAsInt(String name, int defaultValue)
    {
        String env = environment.get(name);
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
