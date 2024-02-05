package org.eclipse.jetty.perf.springboot;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class JettyPerfSpringBootApplication
{
    public static void main(String[] args)
    {
        ConfigurableApplicationContext applicationContext = SpringApplication.run(JettyPerfSpringBootApplication.class, args);
        String[] beanDefinitionNames = applicationContext.getBeanDefinitionNames();
        System.err.println("bean names: " + Arrays.toString(beanDefinitionNames));
    }
}
