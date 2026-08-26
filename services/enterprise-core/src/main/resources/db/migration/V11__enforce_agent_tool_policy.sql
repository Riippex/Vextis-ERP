CREATE UNIQUE INDEX ux_agent_registry_one_active_version
    ON agent_registry_entries (tenant_id, agent_id)
    WHERE status = 'ACTIVE';

UPDATE agent_registry_entries
SET allowed_tools = ARRAY[
    'start_execution_planning',
    'record_execution_plan',
    'evaluate_order_readiness',
    'request_workflow_approval'
]
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_coordinator'
  AND status = 'ACTIVE';

UPDATE agent_registry_entries
SET allowed_tools = ARRAY['lookup_customer']
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_crm_agent'
  AND status = 'ACTIVE';

UPDATE agent_registry_entries
SET allowed_tools = ARRAY['get_stock', 'reserve_stock']
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_inventory_agent'
  AND status = 'ACTIVE';

UPDATE agent_registry_entries
SET allowed_tools = ARRAY['get_credit']
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_billing_agent'
  AND status = 'ACTIVE';
