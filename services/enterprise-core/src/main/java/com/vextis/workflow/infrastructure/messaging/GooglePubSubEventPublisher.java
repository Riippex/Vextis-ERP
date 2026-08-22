package com.vextis.workflow.infrastructure.messaging;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;

import java.util.concurrent.TimeUnit;

final class GooglePubSubEventPublisher implements EventPublisher {

    private static final long PUBLISH_TIMEOUT_SECONDS = 10;

    private final Publisher publisher;

    GooglePubSubEventPublisher(Publisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(OutboxEvent event) {
        PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(event.payload()))
                .putAttributes("event_id", event.eventId())
                .putAttributes("event_type", event.eventType())
                .putAttributes("event_version", Integer.toString(event.eventVersion()))
                .putAttributes("correlation_id", event.correlationId())
                .build();
        try {
            publisher.publish(message).get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pub/Sub publication was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Pub/Sub publication failed", exception);
        }
    }
}
