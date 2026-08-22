package com.vextis.workflow.application;

public interface StartPlanningUseCase {

    PlanningContext startPlanning(StartPlanningCommand command);
}
