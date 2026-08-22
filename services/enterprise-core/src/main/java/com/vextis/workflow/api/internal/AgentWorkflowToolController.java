package com.vextis.workflow.api.internal;

import com.vextis.workflow.application.StartPlanningCommand;
import com.vextis.workflow.application.StartPlanningUseCase;
import com.vextis.workflow.application.WorkflowConflictException;
import com.vextis.workflow.application.WorkflowNotFoundException;
import com.vextis.workflow.domain.Actor;
import com.vextis.workflow.domain.WorkflowExecution;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/internal/agent-tools/v1/workflows")
class AgentWorkflowToolController {

    private final StartPlanningUseCase startPlanning;
    private final AgentToolAuthorizer authorizer;

    AgentWorkflowToolController(StartPlanningUseCase startPlanning, AgentToolAuthorizer authorizer) {
        this.startPlanning = startPlanning;
        this.authorizer = authorizer;
    }

    @PostMapping("/{executionId}/planning")
    ExecutionResponse startPlanning(
            @PathVariable UUID executionId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 16, max = 200) String idempotencyKey,
            @RequestBody @Valid StartPlanningRequest request
    ) {
        authorizer.authorize(authorization, agentId, tenantId);
        try {
            WorkflowExecution execution = startPlanning.startPlanning(new StartPlanningCommand(
                    tenantId,
                    new Actor(Actor.Type.AGENT, agentId),
                    executionId,
                    request.eventId(),
                    correlationId,
                    request.documentUri(),
                    idempotencyKey
            ));
            return ExecutionResponse.from(execution);
        } catch (WorkflowNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (WorkflowConflictException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    record StartPlanningRequest(
            @NotNull UUID eventId,
            @NotBlank @Size(max = 1000) @Pattern(regexp = "^gs://.+") String documentUri
    ) {
    }

    record ExecutionResponse(UUID id, String state, String correlationId, String updatedAt) {

        static ExecutionResponse from(WorkflowExecution execution) {
            return new ExecutionResponse(
                    execution.id(),
                    execution.state().name(),
                    execution.correlationId(),
                    execution.updatedAt().toString()
            );
        }
    }
}
