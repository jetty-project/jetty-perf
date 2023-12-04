package org.eclipse.jetty.perf.util;

import java.io.Serializable;
import java.util.function.Consumer;

public interface SerializableConsumer<T> extends Serializable, Consumer<T>
{
}
