package com.vextis.workflow.infrastructure.messaging;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTests {

    private static final Instant NOW = Instant.parse("2026-08-21T03:30:02Z");

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final OutboxRelay relay = new OutboxRelay(
            repository,
            publisher,
            Clock.fixed(NOW, ZoneOffset.UTC),
            10
    );

    @Test
    void marksEventOnlyAfterPubSubAcknowledgesPublication() {
        OutboxEvent event = event();
        when(repository.lockNextBatch(10)).thenReturn(List.of(event));

        relay.publishPendingEvents();

        var ordered = inOrder(publisher, repository);
        ordered.verify(publisher).publish(event);
        ordered.verify(repository).markPublished(event.eventId(), NOW);
    }

    @Test
    void leavesEventPendingWhenPublicationFails() {
        OutboxEvent event = event();
        when(repository.lockNextBatch(10)).thenReturn(List.of(event));
        doThrow(new IllegalStateException("Pub/Sub unavailable")).when(publisher).publish(event);

        assertThatThrownBy(relay::publishPendingEvents).isInstanceOf(IllegalStateException.class);

        verify(repository, never()).markPublished(event.eventId(), NOW);
    }

    private OutboxEvent event() {
        return new OutboxEvent(
                "8b962f0a-1850-4fcc-a6f5-97e45c67a16e",
                "purchase_order.received",
                2,
                "corr-001",
                "{\"event_version\":2}",
                NOW.minusSeconds(2)
        );
    }
}
