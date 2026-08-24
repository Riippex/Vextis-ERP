package com.vextis.workflow.domain;

public record WorkflowReadinessCheck(
        PlanningDepartment department,
        ReadinessStatus status,
        String detail
) {
    public WorkflowReadinessCheck {
        if (department == null || status == null || detail == null || detail.isBlank() || detail.length() > 500) {
            throw new IllegalArgumentException("Readiness check requires department, status and detail");
        }
        detail = detail.trim();
    }
}
