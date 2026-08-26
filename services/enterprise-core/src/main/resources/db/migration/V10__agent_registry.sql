CREATE TABLE agent_registry_entries (
    tenant_id VARCHAR(100) NOT NULL,
    agent_id VARCHAR(150) NOT NULL,
    version VARCHAR(30) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    department VARCHAR(50) NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    framework VARCHAR(50) NOT NULL,
    model_id VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(30) NOT NULL,
    service_identity VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL,
    capabilities TEXT[] NOT NULL,
    allowed_tools TEXT[] NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, agent_id, version),
    CONSTRAINT ck_agent_registry_status CHECK (status IN ('ACTIVE', 'DRAFT', 'RETIRED'))
);

CREATE INDEX ix_agent_registry_active
    ON agent_registry_entries (tenant_id, status, display_name);

INSERT INTO agent_registry_entries (
    tenant_id, agent_id, version, display_name, department, purpose, framework,
    model_id, prompt_version, service_identity, status, capabilities, allowed_tools
) VALUES
    (
        'demo-tenant', 'vextis_coordinator', '1.0.0', 'Vextis Coordinator',
        'CROSS_DEPARTMENT', 'Routes governed work across the approved specialist fleet.',
        'GOOGLE_ADK', 'gemini-3.5-flash', '1.0.0', 'coordinator-agent', 'ACTIVE',
        ARRAY['department routing', 'cross-department coordination'], ARRAY[]::TEXT[]
    ),
    (
        'demo-tenant', 'vextis_crm_agent', '1.0.0', 'CRM & Sales Agent',
        'CRM_SALES', 'Provides authoritative customer and commercial context.',
        'GOOGLE_ADK', 'gemini-3.5-flash', '1.0.0', 'coordinator-agent', 'ACTIVE',
        ARRAY['customer lookup', 'commercial context'], ARRAY['lookup_customer']
    ),
    (
        'demo-tenant', 'vextis_inventory_agent', '1.0.0', 'Inventory Agent',
        'INVENTORY_OPERATIONS', 'Provides authoritative SKU availability and operations context.',
        'GOOGLE_ADK', 'gemini-3.5-flash', '1.0.0', 'coordinator-agent', 'ACTIVE',
        ARRAY['stock lookup', 'availability context'], ARRAY['get_stock']
    ),
    (
        'demo-tenant', 'vextis_billing_agent', '1.0.0', 'Finance & Billing Agent',
        'FINANCE_BILLING', 'Provides authoritative credit and payment-term context.',
        'GOOGLE_ADK', 'gemini-3.5-flash', '1.0.0', 'coordinator-agent', 'ACTIVE',
        ARRAY['credit lookup', 'payment-term context'], ARRAY['get_credit']
    );
