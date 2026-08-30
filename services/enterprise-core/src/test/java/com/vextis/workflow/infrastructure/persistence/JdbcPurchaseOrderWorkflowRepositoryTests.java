package com.vextis.workflow.infrastructure.persistence;

import com.vextis.workflow.domain.Actor;
import com.vextis.workflow.domain.DuplicatePurchaseOrderException;
import com.vextis.workflow.domain.ExecutionState;
import com.vextis.workflow.domain.ExecutionTimelineEntry;
import com.vextis.workflow.domain.PurchaseOrderReceipt;
import com.vextis.workflow.domain.PurchaseOrderSource;
import com.vextis.workflow.domain.TimelineEntryType;
import com.vextis.workflow.domain.WorkflowExecution;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcPurchaseOrderWorkflowRepositoryTests {

    @Test
    void reportsAConcurrentDuplicatePurchaseOrderWithoutLeakingADatabaseError() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        JdbcPurchaseOrderWorkflowRepository repository =
                new JdbcPurchaseOrderWorkflowRepository(jdbc, mock(ObjectMapper.class));
        UUID purchaseOrderId = UUID.fromString("77cc63cc-3c91-4d80-a918-605b7f231cf8");
        Instant now = Instant.parse("2026-08-30T18:04:50Z");
        PurchaseOrderSource purchaseOrder = new PurchaseOrderSource(
                purchaseOrderId,
                "demo-tenant",
                "PO-2026-001",
                "Acme Colombia",
                "gs://vextis-demo/orders/po-2026-001-copy.pdf",
                now
        );
        WorkflowExecution execution = new WorkflowExecution(
                UUID.fromString("8d3f290d-1322-44a2-8bd7-3b325f170e07"),
                "demo-tenant",
                purchaseOrderId,
                "Process duplicate order",
                ExecutionState.RECEIVED,
                "corr-duplicate",
                now,
                now,
                List.of(new ExecutionTimelineEntry(
                        1, TimelineEntryType.RECEIVED, "Order received", "Ready for planning", now))
        );

        assertThatThrownBy(() -> repository.saveReceivedPurchaseOrder(
                new PurchaseOrderReceipt(purchaseOrder, execution),
                new Actor(Actor.Type.USER, "demo-user"),
                "RECEIVE_PURCHASE_ORDER",
                "receive-po-copy"
        ))
                .isInstanceOf(DuplicatePurchaseOrderException.class)
                .hasMessage("Purchase order PO-2026-001 has already been received.");
    }

    @Test
    void convertsInstantsToUtcOffsetDateTimesForPostgres() {
        Instant instant = Instant.parse("2026-08-24T17:00:00.123Z");

        var timestamp = JdbcPurchaseOrderWorkflowRepository.sqlTimestamp(instant);

        assertThat(timestamp.toInstant()).isEqualTo(instant);
        assertThat(timestamp.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void convertsPostgresOffsetDateTimesBackToInstants() throws Exception {
        var resultSet = mock(java.sql.ResultSet.class);
        var stored = OffsetDateTime.parse("2026-08-24T12:00:00.123-05:00");
        when(resultSet.getObject("occurred_at", OffsetDateTime.class)).thenReturn(stored);

        var instant = JdbcPurchaseOrderWorkflowRepository.readInstant(resultSet, "occurred_at");

        assertThat(instant).isEqualTo(Instant.parse("2026-08-24T17:00:00.123Z"));
    }
}
