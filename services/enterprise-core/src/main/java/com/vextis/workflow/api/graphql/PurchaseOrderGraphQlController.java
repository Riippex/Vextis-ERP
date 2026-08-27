package com.vextis.workflow.api.graphql;

import com.vextis.billing.InvoiceDirectory;
import com.vextis.billing.api.graphql.InvoiceView;
import com.vextis.workflow.application.FindExecutionUseCase;
import com.vextis.workflow.application.ReceivePurchaseOrderCommand;
import com.vextis.workflow.application.ReceivePurchaseOrderUseCase;
import com.vextis.workflow.application.PreparePurchaseOrderUploadCommand;
import com.vextis.workflow.application.PreparePurchaseOrderUploadUseCase;
import com.vextis.workflow.application.DecideApprovalCommand;
import com.vextis.workflow.application.DecideApprovalUseCase;
import com.vextis.shared.security.CurrentActorProvider;
import com.vextis.workflow.domain.ApprovalDecision;
import com.vextis.workflow.domain.WorkflowApproval;
import com.vextis.workflow.domain.Actor;
import com.vextis.workflow.domain.ExecutionTimelineEntry;
import com.vextis.workflow.domain.ExtractedOrderLine;
import com.vextis.workflow.domain.PurchaseOrderReceipt;
import com.vextis.workflow.domain.PurchaseOrderUpload;
import com.vextis.workflow.domain.PurchaseOrderSource;
import com.vextis.workflow.domain.WorkflowExecution;
import com.vextis.workflow.domain.WorkflowPlan;
import com.vextis.workflow.domain.WorkflowPlanStep;
import com.vextis.workflow.domain.WorkflowReadiness;
import com.vextis.workflow.domain.WorkflowReadinessCheck;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Controller
@Validated
class PurchaseOrderGraphQlController {

    private final ReceivePurchaseOrderUseCase receivePurchaseOrder;
    private final PreparePurchaseOrderUploadUseCase preparePurchaseOrderUpload;
    private final FindExecutionUseCase findExecution;
    private final DecideApprovalUseCase decideApproval;
    private final InvoiceDirectory invoices;
    private final CurrentActorProvider currentActor;
    private final String demoTenantId;

    PurchaseOrderGraphQlController(
            ReceivePurchaseOrderUseCase receivePurchaseOrder,
            PreparePurchaseOrderUploadUseCase preparePurchaseOrderUpload,
            FindExecutionUseCase findExecution,
            DecideApprovalUseCase decideApproval,
            InvoiceDirectory invoices,
            CurrentActorProvider currentActor,
            @org.springframework.beans.factory.annotation.Value("${vextis.demo.tenant-id:demo-tenant}") String demoTenantId
    ) {
        this.receivePurchaseOrder = receivePurchaseOrder;
        this.preparePurchaseOrderUpload = preparePurchaseOrderUpload;
        this.findExecution = findExecution;
        this.decideApproval = decideApproval;
        this.invoices = invoices;
        this.currentActor = currentActor;
        this.demoTenantId = demoTenantId;
    }

    @MutationMapping
    PurchaseOrderUploadView preparePurchaseOrderUpload(@Argument @Valid PreparePurchaseOrderUploadInput input) {
        PurchaseOrderUpload upload = preparePurchaseOrderUpload.prepare(new PreparePurchaseOrderUploadCommand(
                demoTenantId,
                new Actor(Actor.Type.USER, currentActor.currentActorId()),
                input.fileName(),
                input.contentType(),
                input.sizeBytes()
        ));
        return PurchaseOrderUploadView.from(upload);
    }

    @MutationMapping
    PurchaseOrderReceiptView receivePurchaseOrder(@Argument @Valid ReceivePurchaseOrderInput input) {
        PurchaseOrderReceipt receipt = receivePurchaseOrder.receive(new ReceivePurchaseOrderCommand(
                demoTenantId,
                new Actor(Actor.Type.USER, currentActor.currentActorId()),
                input.purchaseOrderNumber(),
                input.customerName(),
                input.documentUri(),
                input.idempotencyKey()
        ));
        return PurchaseOrderReceiptView.from(receipt);
    }

    @QueryMapping
    ExecutionView execution(@Argument UUID id) {
        return findExecution.findById(demoTenantId, id).map(this::executionView).orElse(null);
    }

    @MutationMapping
    ExecutionView decideApproval(@Argument @Valid DecideApprovalInput input) {
        WorkflowExecution execution = decideApproval.decideApproval(new DecideApprovalCommand(
                demoTenantId, new Actor(Actor.Type.USER, currentActor.currentActorId()),
                input.executionId(), input.approvalId(), input.decision(), input.reason(), input.idempotencyKey()));
        return executionView(execution);
    }

    record DecideApprovalInput(
            UUID executionId,
            UUID approvalId,
            ApprovalDecision decision,
            @Size(max = 500) String reason,
            @NotBlank @Size(min = 16, max = 200) String idempotencyKey
    ) {
    }

    record ReceivePurchaseOrderInput(
            @NotBlank @Size(max = 100) String purchaseOrderNumber,
            @NotBlank @Size(max = 200) String customerName,
            @NotBlank @Size(max = 1000) @Pattern(regexp = "^gs://.+") String documentUri,
            @NotBlank @Size(max = 200) String idempotencyKey
    ) {
    }

    record PreparePurchaseOrderUploadInput(
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = 100) String contentType,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(10_485_760)
            int sizeBytes
    ) {
    }

    record PurchaseOrderUploadView(
            String uploadUrl,
            String documentUri,
            String expiresAt,
            List<UploadFormFieldView> formFields
    ) {
        static PurchaseOrderUploadView from(PurchaseOrderUpload upload) {
            return new PurchaseOrderUploadView(
                    upload.uploadUrl(),
                    upload.documentUri(),
                    upload.expiresAt().toString(),
                    upload.formFields().stream()
                            .map(field -> new UploadFormFieldView(field.name(), field.value()))
                            .toList()
            );
        }
    }

    record UploadFormFieldView(String name, String value) {
    }

    record PurchaseOrderReceiptView(PurchaseOrderView purchaseOrder, ExecutionView execution) {

        static PurchaseOrderReceiptView from(PurchaseOrderReceipt receipt) {
            return new PurchaseOrderReceiptView(
                    PurchaseOrderView.from(receipt.purchaseOrder()),
                    ExecutionView.from(receipt.execution(), null)
            );
        }
    }

    record PurchaseOrderView(
            UUID id,
            String purchaseOrderNumber,
            String customerName,
            String documentUri,
            String receivedAt
    ) {

        static PurchaseOrderView from(PurchaseOrderSource purchaseOrder) {
            return new PurchaseOrderView(
                    purchaseOrder.id(),
                    purchaseOrder.purchaseOrderNumber(),
                    purchaseOrder.customerName(),
                    purchaseOrder.documentUri(),
                    purchaseOrder.receivedAt().toString()
            );
        }
    }

    record ExecutionView(
            UUID id,
            String goal,
            String state,
            String correlationId,
            String createdAt,
            String updatedAt,
            List<TimelineEntryView> timeline,
            PlanView plan,
            ReadinessView readiness,
            ApprovalView approval,
            InvoiceView invoice
    ) {

        static ExecutionView from(WorkflowExecution execution, InvoiceView invoice) {
            return new ExecutionView(
                    execution.id(),
                    execution.goal(),
                    execution.state().name(),
                    execution.correlationId(),
                    execution.createdAt().toString(),
                    execution.updatedAt().toString(),
                    execution.timeline().stream().map(TimelineEntryView::from).toList(),
                    execution.plan() == null ? null : PlanView.from(execution.plan()),
                    execution.readiness() == null ? null : ReadinessView.from(execution.readiness()),
                    execution.approval() == null ? null : ApprovalView.from(execution.approval()),
                    invoice
            );
        }
    }

    record ApprovalView(
            UUID id, String recommendation, String status, String requestedBy,
            String requestedAt, String expiresAt, String decidedBy, String decidedAt, String reason
    ) {
        static ApprovalView from(WorkflowApproval approval) {
            return new ApprovalView(
                    approval.id(), approval.recommendation(), approval.status().name(), approval.requestedBy(),
                    approval.requestedAt().toString(), approval.expiresAt().toString(), approval.decidedBy(),
                    approval.decidedAt() == null ? null : approval.decidedAt().toString(), approval.reason());
        }
    }

    record PlanView(
            String summary,
            String modelId,
            String generatedAt,
            List<PlanStepView> steps,
            List<OrderLineView> orderLines,
            int requestedPaymentTermsDays,
            String currency
    ) {

        static PlanView from(WorkflowPlan plan) {
            return new PlanView(
                    plan.summary(),
                    plan.modelId(),
                    plan.generatedAt().toString(),
                    plan.steps().stream().map(PlanStepView::from).toList(),
                    plan.orderLines().stream().map(OrderLineView::from).toList(),
                    plan.requestedPaymentTermsDays(),
                    plan.currency()
            );
        }
    }

    record OrderLineView(String sku, int quantity, String unitPrice) {
        static OrderLineView from(ExtractedOrderLine line) {
            return new OrderLineView(
                    line.sku(), line.quantity(), line.unitPrice() == null ? null : line.unitPrice().toPlainString());
        }
    }

    record ReadinessView(String evaluatedAt, List<ReadinessCheckView> checks) {
        static ReadinessView from(WorkflowReadiness readiness) {
            return new ReadinessView(
                    readiness.evaluatedAt().toString(),
                    readiness.checks().stream().map(ReadinessCheckView::from).toList());
        }
    }

    record ReadinessCheckView(String department, String status, String detail) {
        static ReadinessCheckView from(WorkflowReadinessCheck check) {
            return new ReadinessCheckView(check.department().name(), check.status().name(), check.detail());
        }
    }

    record PlanStepView(int sequence, String department, String objective, boolean requiresApproval) {

        static PlanStepView from(WorkflowPlanStep step) {
            return new PlanStepView(
                    step.sequence(),
                    step.department().name(),
                    step.objective(),
                    step.requiresApproval()
            );
        }
    }

    record TimelineEntryView(int sequence, String type, String title, String detail, String occurredAt) {

        static TimelineEntryView from(ExecutionTimelineEntry entry) {
            return new TimelineEntryView(
                    entry.sequence(),
                    entry.type().name(),
                    entry.title(),
                    entry.detail(),
                    entry.occurredAt().toString()
            );
        }
    }

    private ExecutionView executionView(WorkflowExecution execution) {
        InvoiceView invoice = invoices.findByExecution(demoTenantId, execution.id()).map(InvoiceView::from).orElse(null);
        return ExecutionView.from(execution, invoice);
    }
}
