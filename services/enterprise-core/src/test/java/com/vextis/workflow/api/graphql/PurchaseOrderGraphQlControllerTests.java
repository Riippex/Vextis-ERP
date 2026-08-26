package com.vextis.workflow.api.graphql;

import com.vextis.agentregistry.AgentDirectory;
import com.vextis.audit.AuditTrail;
import com.vextis.workflow.application.FindExecutionUseCase;
import com.vextis.workflow.application.ReceivePurchaseOrderCommand;
import com.vextis.workflow.application.ReceivePurchaseOrderUseCase;
import com.vextis.workflow.application.PreparePurchaseOrderUploadCommand;
import com.vextis.workflow.application.PreparePurchaseOrderUploadUseCase;
import com.vextis.workflow.application.DecideApprovalUseCase;
import com.vextis.shared.security.CurrentActorProvider;
import com.vextis.workflow.domain.ExecutionState;
import com.vextis.workflow.domain.ExecutionTimelineEntry;
import com.vextis.workflow.domain.ExtractedOrderLine;
import com.vextis.workflow.domain.PlanningDepartment;
import com.vextis.workflow.domain.PurchaseOrderReceipt;
import com.vextis.workflow.domain.PurchaseOrderSource;
import com.vextis.workflow.domain.PurchaseOrderUpload;
import com.vextis.workflow.domain.TimelineEntryType;
import com.vextis.workflow.domain.WorkflowExecution;
import com.vextis.workflow.domain.WorkflowPlan;
import com.vextis.workflow.domain.WorkflowPlanStep;
import com.vextis.workflow.domain.ReadinessStatus;
import com.vextis.workflow.domain.WorkflowReadiness;
import com.vextis.workflow.domain.WorkflowReadinessCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.test.context.support.WithMockUser;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@GraphQlTest({PurchaseOrderGraphQlController.class, ExecutionAuditGraphQlController.class})
@TestPropertySource(properties = "vextis.exposure=PUBLIC")
class PurchaseOrderGraphQlControllerTests {

    private static final UUID PURCHASE_ORDER_ID = UUID.fromString("77cc63cc-3c91-4d80-a918-605b7f231cf8");
    private static final UUID EXECUTION_ID = UUID.fromString("8d3f290d-1322-44a2-8bd7-3b325f170e07");
    private static final Instant NOW = Instant.parse("2026-08-21T03:30:00Z");

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private ReceivePurchaseOrderUseCase receivePurchaseOrder;

    @MockitoBean
    private PreparePurchaseOrderUploadUseCase preparePurchaseOrderUpload;

    @MockitoBean
    private FindExecutionUseCase findExecution;

    @MockitoBean
    private DecideApprovalUseCase decideApproval;

    @MockitoBean
    private CurrentActorProvider currentActor;

    @MockitoBean
    private AuditTrail auditTrail;

    @MockitoBean
    private AgentDirectory agentDirectory;

    @BeforeEach
    void setUpAuditEvidence() {
        when(auditTrail.findByCorrelation(any(), any())).thenReturn(List.of());
        when(agentDirectory.findAll(any())).thenReturn(List.of());
    }

    @Test
    @WithMockUser(username = "firebase-user-123")
    void preparesTenantScopedPurchaseOrderUpload() {
        when(currentActor.currentActorId()).thenReturn("firebase-user-123");
        when(preparePurchaseOrderUpload.prepare(any(PreparePurchaseOrderUploadCommand.class)))
                .thenReturn(new PurchaseOrderUpload(
                        "https://storage.googleapis.com/signed-upload",
                        "gs://vextis-demo/purchase-orders/tenant/document.pdf",
                        NOW.plusSeconds(600),
                        List.of(new PurchaseOrderUpload.FormField("Content-Type", "application/pdf"))));

        graphQlTester.document("""
                        mutation PreparePurchaseOrderUpload($input: PreparePurchaseOrderUploadInput!) {
                          preparePurchaseOrderUpload(input: $input) {
                            uploadUrl
                            documentUri
                            expiresAt
                            formFields { name value }
                          }
                        }
                        """)
                .variable("input", Map.of(
                        "fileName", "customer-order.pdf",
                        "contentType", "application/pdf",
                        "sizeBytes", 2048))
                .execute()
                .path("preparePurchaseOrderUpload.documentUri")
                .entity(String.class)
                .isEqualTo("gs://vextis-demo/purchase-orders/tenant/document.pdf")
                .path("preparePurchaseOrderUpload.formFields[0].value")
                .entity(String.class)
                .isEqualTo("application/pdf");

        ArgumentCaptor<PreparePurchaseOrderUploadCommand> command =
                ArgumentCaptor.forClass(PreparePurchaseOrderUploadCommand.class);
        verify(preparePurchaseOrderUpload).prepare(command.capture());
        assertThat(command.getValue().actor().id()).isEqualTo("firebase-user-123");
        assertThat(command.getValue().tenantId()).isEqualTo("demo-tenant");
    }

    @Test
    @WithMockUser(username = "firebase-user-123")
    void receivesPurchaseOrderAndReturnsExecutionEvidence() {
        when(currentActor.currentActorId()).thenReturn("firebase-user-123");
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

        ArgumentCaptor<ReceivePurchaseOrderCommand> command =
                ArgumentCaptor.forClass(ReceivePurchaseOrderCommand.class);
        verify(receivePurchaseOrder).receive(command.capture());
        assertThat(command.getValue().actor().id()).isEqualTo("firebase-user-123");
    }

    @Test
    void findsExecutionWithinDemoTenant() {
        when(findExecution.findById(eq("demo-tenant"), eq(EXECUTION_ID)))
                .thenReturn(Optional.of(runningExecution()));
        when(auditTrail.findByCorrelation("demo-tenant", "corr-001")).thenReturn(List.of(
                new AuditTrail.AuditRecord(
                        UUID.fromString("24fe5be0-ff46-4c88-ab46-29fd01f4036a"),
                        "corr-001", "AGENT", "vextis_coordinator", "RECORD_EXECUTION_PLAN",
                        "WORKFLOW_EXECUTION", EXECUTION_ID, "SUCCEEDED", NOW)));
        when(agentDirectory.findAll("demo-tenant")).thenReturn(List.of(
                new AgentDirectory.AgentRegistration(
                        "vextis_coordinator", "1.0.0", "Vextis Coordinator", "CROSS_DEPARTMENT",
                        "Coordinates approved specialist work.", "GOOGLE_ADK", "gemini-3.5-flash",
                        "1.0.0", "coordinator-agent", "ACTIVE", List.of("coordination"), List.of())));

        graphQlTester.document("""
                        query FindExecution($id: ID!) {
                          execution(id: $id) {
                            id
                            state
                            correlationId
                            timeline { sequence }
                            plan {
                              modelId
                              requestedPaymentTermsDays
                              orderLines { sku quantity }
                              steps { sequence department requiresApproval }
                            }
                            readiness { checks { department status detail } }
                            auditTrail {
                              actorType
                              actorId
                              toolName
                              result
                              approvedAgent { version modelId promptVersion serviceIdentity }
                            }
                          }
                        }
                        """)
                .variable("id", EXECUTION_ID.toString())
                .execute()
                .path("execution.id")
                .entity(String.class)
                .isEqualTo(EXECUTION_ID.toString())
                .path("execution.plan.modelId")
                .entity(String.class)
                .isEqualTo("gemini-3.5-flash")
                .path("execution.plan.steps[0].department")
                .entity(String.class)
                .isEqualTo("CRM_SALES")
                .path("execution.plan.orderLines[0].sku")
                .entity(String.class)
                .isEqualTo("VXT-CHAIR-01")
                .path("execution.readiness.checks[0].status")
                .entity(String.class)
                .isEqualTo("READY")
                .path("execution.auditTrail[0].toolName")
                .entity(String.class)
                .isEqualTo("record_execution_plan")
                .path("execution.auditTrail[0].approvedAgent.modelId")
                .entity(String.class)
                .isEqualTo("gemini-3.5-flash");
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

    private WorkflowExecution runningExecution() {
        WorkflowExecution planning = new WorkflowExecution(
                EXECUTION_ID,
                "demo-tenant",
                PURCHASE_ORDER_ID,
                "Procesar la orden PO-2026-001 de Acme Colombia",
                ExecutionState.PLANNING,
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
        WorkflowExecution running = planning.recordPlan(
                new WorkflowPlan(
                        "Validate the customer and order.",
                        "gemini-3.5-flash",
                        NOW,
                        List.of(new WorkflowPlanStep(
                                1,
                                PlanningDepartment.CRM_SALES,
                                "Validate customer context.",
                                false
                        )),
                        List.of(new ExtractedOrderLine("VXT-CHAIR-01", 10)),
                        30
                ),
                NOW
        );
        return running.recordReadiness(
                new WorkflowReadiness(NOW, List.of(
                        new WorkflowReadinessCheck(
                                PlanningDepartment.CRM_SALES, ReadinessStatus.READY, "Customer matched."),
                        new WorkflowReadinessCheck(
                                PlanningDepartment.INVENTORY_OPERATIONS, ReadinessStatus.READY, "Stock available."),
                        new WorkflowReadinessCheck(
                                PlanningDepartment.FINANCE_BILLING, ReadinessStatus.READY, "Terms accepted.")
                )),
                NOW
        );
    }
}
