package com.vextis.workflow.api.graphql;

import com.vextis.workflow.application.FindExecutionUseCase;
import com.vextis.workflow.application.ReceivePurchaseOrderCommand;
import com.vextis.workflow.application.ReceivePurchaseOrderUseCase;
import com.vextis.workflow.domain.ExecutionState;
import com.vextis.workflow.domain.ExecutionTimelineEntry;
import com.vextis.workflow.domain.PurchaseOrderReceipt;
import com.vextis.workflow.domain.PurchaseOrderSource;
import com.vextis.workflow.domain.TimelineEntryType;
import com.vextis.workflow.domain.WorkflowExecution;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GraphQlTest(PurchaseOrderGraphQlController.class)
class PurchaseOrderGraphQlControllerTests {

    private static final UUID PURCHASE_ORDER_ID = UUID.fromString("77cc63cc-3c91-4d80-a918-605b7f231cf8");
    private static final UUID EXECUTION_ID = UUID.fromString("8d3f290d-1322-44a2-8bd7-3b325f170e07");
    private static final Instant NOW = Instant.parse("2026-08-21T03:30:00Z");

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private ReceivePurchaseOrderUseCase receivePurchaseOrder;

    @MockitoBean
    private FindExecutionUseCase findExecution;

    @Test
    void receivesPurchaseOrderAndReturnsExecutionEvidence() {
        when(receivePurchaseOrder.receive(any(ReceivePurchaseOrderCommand.class))).thenReturn(receipt());

        graphQlTester.documentName("receive-purchase-order")
                .variable("input", Map.of(
                        "purchaseOrderNumber", "PO-2026-001",
                        "customerName", "Acme Colombia",
                        "documentUri", "gs://vextis-demo/orders/po-2026-001.pdf",
                        "idempotencyKey", "receive-po-001"
                ))
                .execute()
                .path("receivePurchaseOrder.purchaseOrder.id")
                .entity(String.class)
                .isEqualTo(PURCHASE_ORDER_ID.toString())
                .path("receivePurchaseOrder.execution.state")
                .entity(String.class)
                .isEqualTo("RECEIVED")
                .path("receivePurchaseOrder.execution.timeline[0].title")
                .entity(String.class)
                .isEqualTo("Orden recibida");

        verify(receivePurchaseOrder).receive(any(ReceivePurchaseOrderCommand.class));
    }

    @Test
    void findsExecutionWithinDemoTenant() {
        when(findExecution.findById(eq("demo-tenant"), eq(EXECUTION_ID)))
                .thenReturn(Optional.of(receipt().execution()));

        graphQlTester.document("""
                        query FindExecution($id: ID!) {
                          execution(id: $id) { id state correlationId timeline { sequence } }
                        }
                        """)
                .variable("id", EXECUTION_ID.toString())
                .execute()
                .path("execution.id")
                .entity(String.class)
                .isEqualTo(EXECUTION_ID.toString());
    }

    private PurchaseOrderReceipt receipt() {
        PurchaseOrderSource purchaseOrder = new PurchaseOrderSource(
                PURCHASE_ORDER_ID,
                "demo-tenant",
                "PO-2026-001",
                "Acme Colombia",
                "gs://vextis-demo/orders/po-2026-001.pdf",
                NOW
        );
        WorkflowExecution execution = new WorkflowExecution(
                EXECUTION_ID,
                "demo-tenant",
                PURCHASE_ORDER_ID,
                "Procesar la orden PO-2026-001 de Acme Colombia",
                ExecutionState.RECEIVED,
                "corr-001",
                NOW,
                NOW,
                List.of(new ExecutionTimelineEntry(
                        1,
                        TimelineEntryType.RECEIVED,
                        "Orden recibida",
                        "Lista para planificación.",
                        NOW
                ))
        );
        return new PurchaseOrderReceipt(purchaseOrder, execution);
    }
}
