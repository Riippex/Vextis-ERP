package com.vextis.workflow.api.internal;

import com.vextis.billing.Invoice;
import com.vextis.workflow.application.IssueApprovedInvoiceCommand;
import com.vextis.workflow.application.IssueApprovedInvoiceUseCase;
import com.vextis.workflow.application.WorkflowConflictException;
import com.vextis.workflow.application.WorkflowNotFoundException;
import com.vextis.workflow.domain.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/internal/agent-tools/v1/billing/orders")
class AgentBillingToolController {
    private final IssueApprovedInvoiceUseCase issueInvoice;
    private final AgentToolAuthorizer authorizer;
    private final AgentAuthorizationDenialRecorder denialRecorder;

    AgentBillingToolController(
            IssueApprovedInvoiceUseCase issueInvoice,
            AgentToolAuthorizer authorizer,
            AgentAuthorizationDenialRecorder denialRecorder
    ) {
        this.issueInvoice = issueInvoice;
        this.authorizer = authorizer;
        this.denialRecorder = denialRecorder;
    }

    @PostMapping("/{orderId}/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    InvoiceResponse issue(
            @PathVariable UUID orderId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 16, max = 200) String idempotencyKey,
            @RequestBody @Valid IssueInvoiceRequest request
    ) {
        try {
            authorizer.authorize(authorization, agentId, tenantId, AgentTool.CREATE_INVOICE);
        } catch (ResponseStatusException exception) {
            if (HttpStatus.FORBIDDEN.equals(exception.getStatusCode())) {
                denialRecorder.recordSafely(tenantId, agentId, correlationId, request.executionId(),
                        AgentAuthorizationDenialRecorder.WorkflowTool.CREATE_INVOICE);
            }
            throw exception;
        }
        try {
            return InvoiceResponse.from(issueInvoice.issueInvoice(new IssueApprovedInvoiceCommand(
                    tenantId, new Actor(Actor.Type.AGENT, agentId), orderId, request.executionId(),
                    correlationId, idempotencyKey)));
        } catch (WorkflowNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (WorkflowConflictException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    record IssueInvoiceRequest(@NotNull UUID executionId) {
    }

    record InvoiceResponse(
            UUID id, UUID orderId, UUID executionId, String customerName, String currency,
            String subtotal, String tax, String total, String status, int paymentTermsDays,
            String issuedAt, String correlationId, List<InvoiceLineResponse> lines
    ) {
        static InvoiceResponse from(Invoice invoice) {
            return new InvoiceResponse(
                    invoice.id(), invoice.orderId(), invoice.executionId(), invoice.customerName(), invoice.currency(),
                    invoice.subtotal().toPlainString(), invoice.tax().toPlainString(), invoice.total().toPlainString(),
                    invoice.status().name(), invoice.paymentTermsDays(), invoice.issuedAt().toString(),
                    invoice.correlationId(), invoice.lines().stream().map(InvoiceLineResponse::from).toList());
        }
    }

    record InvoiceLineResponse(String sku, int quantity, String unitPrice, String lineSubtotal) {
        static InvoiceLineResponse from(Invoice.Line line) {
            return new InvoiceLineResponse(
                    line.sku(), line.quantity(), line.unitPrice().toPlainString(), line.lineSubtotal().toPlainString());
        }
    }
}
