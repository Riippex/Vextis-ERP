package com.vextis.workflow.application;

import com.vextis.workflow.domain.WorkflowExecution;

import java.util.Optional;
import java.util.UUID;

public interface FindExecutionUseCase {

    Optional<WorkflowExecution> findById(String tenantId, UUID executionId);
}
