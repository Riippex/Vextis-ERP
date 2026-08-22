package com.vextis.workflow.infrastructure.messaging;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.TopicName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "vextis.pubsub.enabled", havingValue = "true")
class PubSubConfiguration {

    @Bean(destroyMethod = "shutdown")
    Publisher orderEventsPublisher(
            @Value("${vextis.pubsub.project-id}") String projectId,
            @Value("${vextis.pubsub.topic-id}") String topicId
    ) throws IOException {
        if (projectId.isBlank() || topicId.isBlank()) {
            throw new IllegalStateException("Pub/Sub project and topic must be configured when enabled");
        }
        return Publisher.newBuilder(TopicName.of(projectId, topicId)).build();
    }

    @Bean
    EventPublisher eventPublisher(Publisher orderEventsPublisher) {
        return new GooglePubSubEventPublisher(orderEventsPublisher);
    }
}
