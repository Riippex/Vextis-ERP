package com.vextis.workflow.infrastructure.messaging;

interface EventPublisher {

    void publish(OutboxEvent event);
}
