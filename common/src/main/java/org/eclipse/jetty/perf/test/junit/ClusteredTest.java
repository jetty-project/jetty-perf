package org.eclipse.jetty.perf.test.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Inherited
@Retention(RUNTIME)
@Target({ElementType.PARAMETER })
@ExtendWith(ClusteredTestParameterResolver.class)
public @interface ClusteredTest
{
}
