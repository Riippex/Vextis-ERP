package com.vextis.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ExecutionOverview {

    List<ExecutionSummary> findRecent(String tenantId, int limit);

    List<DepartmentVolume> volumeByDepartment(String tenantId);

    record ExecutionSummary(
            UUID id,
            String purchaseOrderNumber,
            String customerName,
            String state,
            String correlationId,
            Instant updatedAt
    ) {
    }

    /**
     * An execution counts once per department it has a plan step in, so totals across
     * departments can exceed the execution count for multi-department orders.
     */
    record DepartmentVolume(String department, int count) {
    }
}
