package com.vextis.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ExecutionOverview {

    List<ExecutionSummary> findRecent(String tenantId, int limit);

    record ExecutionSummary(
            UUID id,
            String purchaseOrderNumber,
            String customerName,
            String state,
            String correlationId,
            Instant updatedAt
    ) {
    }
}
