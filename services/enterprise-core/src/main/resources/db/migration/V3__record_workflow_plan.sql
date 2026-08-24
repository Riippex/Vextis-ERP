CREATE TABLE workflow_execution_plans (
    execution_id UUID PRIMARY KEY REFERENCES workflow_executions(id) ON DELETE CASCADE,
    summary VARCHAR(500) NOT NULL,
    model_id VARCHAR(150) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE workflow_execution_plan_steps (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES workflow_execution_plans(execution_id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    department VARCHAR(50) NOT NULL,
    objective VARCHAR(500) NOT NULL,
    requires_approval BOOLEAN NOT NULL,
    CONSTRAINT uq_workflow_plan_step_sequence UNIQUE (execution_id, sequence_number),
    CONSTRAINT ck_workflow_plan_step_sequence CHECK (sequence_number BETWEEN 1 AND 5),
    CONSTRAINT ck_workflow_plan_step_department CHECK (
        department IN ('CRM_SALES', 'INVENTORY_OPERATIONS', 'FINANCE_BILLING')
    )
);

CREATE INDEX ix_workflow_plan_steps_execution
    ON workflow_execution_plan_steps (execution_id, sequence_number);
