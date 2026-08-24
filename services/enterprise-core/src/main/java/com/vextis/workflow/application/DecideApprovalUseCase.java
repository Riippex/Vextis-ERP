package com.vextis.workflow.application;

import com.vextis.workflow.domain.WorkflowExecution;

public interface DecideApprovalUseCase {
    WorkflowExecution decideApproval(DecideApprovalCommand command);
}
