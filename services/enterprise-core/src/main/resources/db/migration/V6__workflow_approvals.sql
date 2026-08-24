CREATE TABLE workflow_approvals (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL UNIQUE REFERENCES workflow_executions(id) ON DELETE CASCADE,
    recommendation VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_by VARCHAR(150) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    decided_by VARCHAR(150),
    decided_at TIMESTAMPTZ,
    decision_reason VARCHAR(500),
    CONSTRAINT ck_workflow_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_workflow_approval_expiration CHECK (expires_at > requested_at),
    CONSTRAINT ck_workflow_approval_decision CHECK (
        (status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
        OR (status <> 'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
    )
);
