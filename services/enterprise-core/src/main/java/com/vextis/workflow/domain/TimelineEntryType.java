package com.vextis.workflow.domain;

public enum TimelineEntryType {
    RECEIVED,
    STATUS_CHANGED,
    APPROVAL_REQUESTED,
    APPROVAL_DECIDED,
    COMPLETED,
    INVOICE_ISSUED,
    FAILED
}
