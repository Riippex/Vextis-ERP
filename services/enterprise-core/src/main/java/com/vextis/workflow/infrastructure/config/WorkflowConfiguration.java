package com.vextis.workflow.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class WorkflowConfiguration {

    @Bean
    Clock workflowClock() {
        return Clock.systemUTC();
    }
}
