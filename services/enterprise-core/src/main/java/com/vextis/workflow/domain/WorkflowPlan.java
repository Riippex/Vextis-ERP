package com.vextis.workflow.domain;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

public record WorkflowPlan(
        String summary,
        String modelId,
        Instant generatedAt,
        List<WorkflowPlanStep> steps,
        List<ExtractedOrderLine> orderLines,
        int requestedPaymentTermsDays
) {

    public WorkflowPlan {
        if (summary == null || summary.isBlank() || summary.length() > 500) {
            throw new IllegalArgumentException("Plan summary is required and must not exceed 500 characters");
        }
        if (modelId == null || modelId.isBlank() || modelId.length() > 150 || generatedAt == null) {
            throw new IllegalArgumentException("Plan model and generation time are required");
        }
        if (steps == null || steps.isEmpty() || steps.size() > 5) {
            throw new IllegalArgumentException("Plan must contain between 1 and 5 steps");
        }
        summary = summary.trim();
        modelId = modelId.trim();
        List<WorkflowPlanStep> validatedSteps = List.copyOf(steps);
        boolean contiguous = IntStream.range(0, validatedSteps.size())
                .allMatch(index -> validatedSteps.get(index).sequence() == index + 1);
        if (!contiguous) {
            throw new IllegalArgumentException("Plan step sequence must be contiguous and start at 1");
        }
        steps = validatedSteps;
        if (orderLines == null || orderLines.isEmpty() || orderLines.size() > 20) {
            throw new IllegalArgumentException("Plan must contain between 1 and 20 extracted order lines");
        }
        orderLines = List.copyOf(orderLines);
        if (orderLines.stream().map(ExtractedOrderLine::sku).distinct().count() != orderLines.size()) {
            throw new IllegalArgumentException("Extracted order line SKUs must be unique");
        }
        if (requestedPaymentTermsDays < 0 || requestedPaymentTermsDays > 365) {
            throw new IllegalArgumentException("Requested payment terms must be between 0 and 365 days");
        }
    }
}
