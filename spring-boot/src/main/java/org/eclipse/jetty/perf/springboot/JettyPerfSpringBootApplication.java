package org.eclipse.jetty.perf.springboot;

import org.eclipse.jetty.perf.test.PerfTestParams;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class JettyPerfSpringBootApplication
{
    public static PerfTestParams perfTestParams;

    public static void main(String[] args)
    {
        SpringApplication.run(JettyPerfSpringBootApplication.class, args);
    }

    @Bean
    public PerfTestParams getPerfTestParams()
    {
        return perfTestParams;
    }
}
