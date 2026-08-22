package com.vextis.workflow.application;

import com.vextis.workflow.domain.WorkflowExecution;

public interface StartPlanningUseCase {

    WorkflowExecution startPlanning(StartPlanningCommand command);
}
