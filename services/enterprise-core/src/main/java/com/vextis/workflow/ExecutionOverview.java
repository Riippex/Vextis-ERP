package com.vextis.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ExecutionOverview {

    List<ExecutionSummary> findRecent(String tenantId, int limit);

    CustomerOrders findCustomerOrders(String tenantId, String legalName, int limit);

    List<DepartmentVolume> volumeByDepartment(String tenantId);

    List<WeeklyVolume> completedPerWeek(String tenantId, int weeks);

    record ExecutionSummary(
            UUID id,
            String purchaseOrderNumber,
            String customerName,
            String state,
            String correlationId,
            Instant updatedAt
    ) {
    }

    record CustomerOrders(int totalCount, List<ExecutionSummary> orders) {
        public CustomerOrders {
            orders = List.copyOf(orders);
        }
    }

    /**
     * An execution counts once per department it has a plan step in, so totals across
     * departments can exceed the execution count for multi-department orders.
     */
    record DepartmentVolume(String department, int count) {
    }

    record WeeklyVolume(Instant weekStart, int count) {
    }
}
