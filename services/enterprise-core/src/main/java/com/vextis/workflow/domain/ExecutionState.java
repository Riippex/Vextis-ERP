package com.vextis.workflow.domain;

public enum ExecutionState {
    RECEIVED,
    PLANNING,
    RUNNING,
    WAITING_APPROVAL,
    COMPLETED,
    FAILED
}
