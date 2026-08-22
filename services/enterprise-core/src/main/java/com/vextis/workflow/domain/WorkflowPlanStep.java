package com.vextis.workflow.domain;

public record WorkflowPlanStep(
        int sequence,
        PlanningDepartment department,
        String objective,
        boolean requiresApproval
) {

    public WorkflowPlanStep {
        if (sequence < 1 || sequence > 5) {
            throw new IllegalArgumentException("Plan step sequence must be between 1 and 5");
        }
        if (department == null || objective == null || objective.isBlank() || objective.length() > 500) {
            throw new IllegalArgumentException("Plan step department and objective are required");
        }
        objective = objective.trim();
    }
}
