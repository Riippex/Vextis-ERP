package com.vextis.workflow.api.internal;

import com.vextis.audit.AuditTrail;
import com.vextis.workflow.application.FindExecutionUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
class AgentAuthorizationDenialRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentAuthorizationDenialRecorder.class);

    private final FindExecutionUseCase findExecution;
    private final AuditTrail auditTrail;
    private final Clock clock;
    private final String allowedTenantId;

    AgentAuthorizationDenialRecorder(
            FindExecutionUseCase findExecution,
            AuditTrail auditTrail,
            Clock clock,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String allowedTenantId
    ) {
        this.findExecution = findExecution;
        this.auditTrail = auditTrail;
        this.clock = clock;
        this.allowedTenantId = allowedTenantId;
    }

    void recordSafely(
            String tenantId,
            String agentId,
            String correlationId,
            UUID executionId,
            WorkflowTool tool
    ) {
        if (!allowedTenantId.equals(tenantId)) {
            return;
        }
        try {
            findExecution.findById(allowedTenantId, executionId)
                    .filter(execution -> execution.correlationId().equals(correlationId))
                    .ifPresent(execution -> auditTrail.recordAgentDecision(new AuditTrail.AgentDecision(
                            allowedTenantId,
                            execution.correlationId(),
                            agentId,
                            tool.name(),
                            "WORKFLOW_EXECUTION",
                            execution.id(),
                            AuditTrail.AgentDecisionResult.DENIED,
                            clock.instant()
                    )));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not persist denied agent tool attempt for execution {} and tool {}",
                    executionId,
                    tool,
                    exception
            );
        }
    }

    enum WorkflowTool {
        START_EXECUTION_PLANNING,
        RECORD_EXECUTION_PLAN,
        EVALUATE_ORDER_READINESS,
        REQUEST_WORKFLOW_APPROVAL,
        CREATE_INVOICE
    }
}
