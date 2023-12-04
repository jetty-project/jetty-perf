package org.eclipse.jetty.perf.test.junit;

import java.lang.reflect.Method;

import org.eclipse.jetty.perf.test.ClusteredTestContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class ClusteredTestParameterResolver implements ParameterResolver
{
    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException
    {
        Class<?> type = parameterContext.getParameter().getType();
        return type == ClusteredTestContext.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException
    {
        try
        {
            Class<?> testClass = extensionContext.getTestClass().orElseThrow();
            Method testMethod = extensionContext.getTestMethod().orElseThrow();
            ClusteredTestContext clusteredTestContext = new ClusteredTestContext(testClass, testMethod);
            extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).put("ClusteredTestContext", (ExtensionContext.Store.CloseableResource)clusteredTestContext::close);
            return clusteredTestContext;
        }
        catch (Exception e)
        {
            throw new ParameterResolutionException("cannot instantiate ClusteredTestContext", e);
        }
    }
}
