package org.eclipse.jetty.perf.httpclient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.component.DumpableCollection;

public class StatisticalSyncSocketAddressResolver extends SocketAddressResolver.Sync implements Dumpable
{
    private final ConcurrentMap<String, Success> successes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Failure> failures = new ConcurrentHashMap<>();
    private final LongAdder resolves = new LongAdder();

    @Override
    public void resolve(String host, int port, Promise<List<InetSocketAddress>> promise)
    {
        resolves.increment();
        Promise<List<InetSocketAddress>> p = new Promise.Wrapper<>(promise)
        {
            final long begin = System.nanoTime();

            @Override
            public void succeeded(List<InetSocketAddress> result)
            {
                successes.compute(host + ":" + port, (k, v) ->
                {
                    if (v == null)
                        v = new Success();
                    v.count++;
                    v.totalTimeNs += System.nanoTime() - begin;
                    return v;
                });
                super.succeeded(result);
            }

            @Override
            public void failed(Throwable x)
            {
                failures.compute(host + ":" + port, (k, v) ->
                {
                    if (v == null)
                        v = new Failure();
                    v.count++;
                    v.totalTimeNs += System.nanoTime() - begin;
                    v.failures.add(x.getClass());
                    return v;
                });
                super.failed(x);
            }
        };
        super.resolve(host, port, p);
    }

    @Override
    public String toString()
    {
        return "StatisticalSyncSocketAddressResolver{" +
            "resolves=" + resolves +
            '}';
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException
    {
        Dumpable.dumpObjects(
            out,
            indent,
            this,
            DumpableCollection.from("successes", successes.entrySet()),
            DumpableCollection.from("failures", failures.entrySet()));
    }

    static class Success
    {
        long count;
        long totalTimeNs;

        @Override
        public String toString()
        {
            return "count:" + count + ",time:" + TimeUnit.NANOSECONDS.toMillis(totalTimeNs) + "ms";
        }
    }

    static class Failure
    {
        long count;
        long totalTimeNs;
        Set<Class<? extends Throwable>> failures = new HashSet<>();

        @Override
        public String toString()
        {
            return "count:" + count + ",time:" + TimeUnit.NANOSECONDS.toMillis(totalTimeNs) + "ms,failures:" + failures;
        }
    }
}
