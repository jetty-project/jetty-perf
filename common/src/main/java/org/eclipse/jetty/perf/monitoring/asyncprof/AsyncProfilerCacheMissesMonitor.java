package org.eclipse.jetty.perf.monitoring.asyncprof;

public class AsyncProfilerCacheMissesMonitor extends AbstractAsyncProfilerMonitor
{
    public AsyncProfilerCacheMissesMonitor() throws Exception
    {
        super("cache-misses");
    }
}
