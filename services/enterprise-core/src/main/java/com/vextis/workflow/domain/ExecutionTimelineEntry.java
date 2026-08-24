package com.vextis.workflow.domain;

import java.time.Instant;

public record ExecutionTimelineEntry(
        int sequence,
        TimelineEntryType type,
        String title,
        String detail,
        Instant occurredAt
) {

    public ExecutionTimelineEntry {
        if (sequence < 1) {
            throw new IllegalArgumentException("Timeline sequence must be positive");
        }
        if (type == null || occurredAt == null) {
            throw new IllegalArgumentException("Timeline type and timestamp are required");
        }
        if (title == null || title.isBlank() || detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("Timeline title and detail are required");
        }
    }
}
