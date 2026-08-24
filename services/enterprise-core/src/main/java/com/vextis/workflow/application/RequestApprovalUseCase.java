package com.vextis.workflow.application;

import com.vextis.workflow.domain.WorkflowExecution;

public interface RequestApprovalUseCase {
    WorkflowExecution requestApproval(RequestApprovalCommand command);
}
