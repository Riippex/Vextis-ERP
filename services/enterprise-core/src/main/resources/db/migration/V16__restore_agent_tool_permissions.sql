-- V15 rewrote allowed_tools with absolute ARRAY[...] assignments instead of
-- appending 'search_knowledge_base', so it silently revoked the permissions
-- V11 and V14 had granted:
--   vextis_coordinator     lost start_execution_planning, record_execution_plan,
--                          evaluate_order_readiness and request_workflow_approval
--   vextis_inventory_agent lost reserve_stock
-- V15 may already be applied in an environment, so this compensating migration
-- restores the intended union rather than editing V15 in place. The assignments
-- below are absolute and idempotent: they state the complete, authoritative tool
-- allowlist per agent, which is what Enterprise Core enforces on every
-- /internal/agent-tools/** call.

UPDATE agent_registry_entries
SET allowed_tools = ARRAY[
    'start_execution_planning',
    'record_execution_plan',
    'evaluate_order_readiness',
    'request_workflow_approval',
    'search_knowledge_base'
]
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_coordinator'
  AND status = 'ACTIVE';

UPDATE agent_registry_entries
SET allowed_tools = ARRAY[
    'lookup_customer',
    'search_knowledge_base'
]
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_crm_agent'
  AND status = 'ACTIVE';

UPDATE agent_registry_entries
SET allowed_tools = ARRAY[
    'get_stock',
    'reserve_stock',
    'search_knowledge_base'
]
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_inventory_agent'
  AND status = 'ACTIVE';

UPDATE agent_registry_entries
SET allowed_tools = ARRAY[
    'get_credit',
    'create_invoice',
    'search_knowledge_base'
]
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_billing_agent'
  AND status = 'ACTIVE';
