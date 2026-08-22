package com.vextis.workflow.infrastructure.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
@ConditionalOnProperty(name = "vextis.pubsub.enabled", havingValue = "true")
class OutboxRelay {

    private final OutboxEventRepository repository;
    private final EventPublisher publisher;
    private final Clock clock;
    private final int batchSize;

    OutboxRelay(
            OutboxEventRepository repository,
            EventPublisher publisher,
            Clock clock,
            @Value("${vextis.pubsub.batch-size:10}") int batchSize
    ) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Outbox batch size must be between 1 and 100");
        }
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${vextis.pubsub.fixed-delay:2s}")
    @Transactional
    public void publishPendingEvents() {
        for (OutboxEvent event : repository.lockNextBatch(batchSize)) {
            publisher.publish(event);
            repository.markPublished(event.eventId(), clock.instant());
        }
    }
}
