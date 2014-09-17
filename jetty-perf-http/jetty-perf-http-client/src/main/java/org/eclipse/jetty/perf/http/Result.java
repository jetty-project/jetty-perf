//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.perf.http;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jetty.toolchain.perf.MeasureRecorder;

public class Result implements MeasureRecorder.Converter
{
    private final MeasureRecorder measures = new MeasureRecorder(this, "latency", "ms");
    public final long precision = 1000;
    public final AtomicLong elapsed = new AtomicLong();
    public final AtomicLong sndThroughput = new AtomicLong();
    public final AtomicLong rcvThroughput = new AtomicLong();
    public final AtomicLong sent = new AtomicLong();
    public final AtomicLong received = new AtomicLong();
    public final AtomicLong rcvBeginTime = new AtomicLong();
    public final AtomicLong rcvEndTime = new AtomicLong();

    public void latency(long latency)
    {
        measures.record(latency, true);
    }

    @Override
    public long convert(long latency)
    {
        return TimeUnit.NANOSECONDS.toMillis(latency);
    }

    @Override
    public String toString()
    {
        return String.format("" +
                "%s requests at %f requests/s%n" +
                "%s responses at %f responses/s%n" +
                "%s",
                sent, (float)sndThroughput.get() / precision,
                received, (float)rcvThroughput.get() / precision,
                measures.snapshot());
    }
}
