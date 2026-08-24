package com.vextis.workflow.application;

import com.vextis.workflow.domain.WorkflowExecution;

public interface EvaluateReadinessUseCase {
    WorkflowExecution evaluateReadiness(EvaluateReadinessCommand command);
}
