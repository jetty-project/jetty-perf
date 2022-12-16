package org.eclipse.jetty.perf.util;

import java.io.Serializable;
import java.util.function.Supplier;

public interface SerializableSupplier<T> extends Serializable, Supplier<T>
{
}
