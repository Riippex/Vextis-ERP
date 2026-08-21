package com.vextis.workflow.api.graphql;

import com.vextis.workflow.application.FindExecutionUseCase;
import com.vextis.workflow.application.ReceivePurchaseOrderCommand;
import com.vextis.workflow.application.ReceivePurchaseOrderUseCase;
import com.vextis.workflow.domain.Actor;
import com.vextis.workflow.domain.ExecutionTimelineEntry;
import com.vextis.workflow.domain.PurchaseOrderReceipt;
import com.vextis.workflow.domain.PurchaseOrderSource;
import com.vextis.workflow.domain.WorkflowExecution;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
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
    private final FindExecutionUseCase findExecution;
    private final String demoTenantId;
    private final String demoActorId;

    PurchaseOrderGraphQlController(
            ReceivePurchaseOrderUseCase receivePurchaseOrder,
            FindExecutionUseCase findExecution,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String demoTenantId,
            @Value("${vextis.demo.actor-id:demo-user}") String demoActorId
    ) {
        this.receivePurchaseOrder = receivePurchaseOrder;
        this.findExecution = findExecution;
        this.demoTenantId = demoTenantId;
        this.demoActorId = demoActorId;
    }

    @MutationMapping
    PurchaseOrderReceiptView receivePurchaseOrder(@Argument @Valid ReceivePurchaseOrderInput input) {
        PurchaseOrderReceipt receipt = receivePurchaseOrder.receive(new ReceivePurchaseOrderCommand(
                demoTenantId,
                new Actor(Actor.Type.USER, demoActorId),
                input.purchaseOrderNumber(),
                input.customerName(),
                input.documentUri(),
                input.idempotencyKey()
        ));
        return PurchaseOrderReceiptView.from(receipt);
    }

    @QueryMapping
    ExecutionView execution(@Argument UUID id) {
        return findExecution.findById(demoTenantId, id).map(ExecutionView::from).orElse(null);
    }

    record ReceivePurchaseOrderInput(
            @NotBlank @Size(max = 100) String purchaseOrderNumber,
            @NotBlank @Size(max = 200) String customerName,
            @NotBlank @Size(max = 1000) @Pattern(regexp = "^gs://.+") String documentUri,
            @NotBlank @Size(max = 200) String idempotencyKey
    ) {
    }

    record PurchaseOrderReceiptView(PurchaseOrderView purchaseOrder, ExecutionView execution) {

        static PurchaseOrderReceiptView from(PurchaseOrderReceipt receipt) {
            return new PurchaseOrderReceiptView(
                    PurchaseOrderView.from(receipt.purchaseOrder()),
                    ExecutionView.from(receipt.execution())
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
            List<TimelineEntryView> timeline
    ) {

        static ExecutionView from(WorkflowExecution execution) {
            return new ExecutionView(
                    execution.id(),
                    execution.goal(),
                    execution.state().name(),
                    execution.correlationId(),
                    execution.createdAt().toString(),
                    execution.updatedAt().toString(),
                    execution.timeline().stream().map(TimelineEntryView::from).toList()
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
}
