package com.vextis.workflow.application;

import com.vextis.workflow.domain.WorkflowExecution;

public interface RecordPlanUseCase {

    WorkflowExecution recordPlan(RecordPlanCommand command);
}
