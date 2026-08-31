-- Bounded, read-only discovery tools used by text and Live specialists.
-- These remain explicitly allowlisted per logical agent and cannot mutate Core.

UPDATE agent_registry_entries
SET allowed_tools = ARRAY[
    'lookup_customer',
    'list_customers',
    'search_customer_orders',
    'register_quote_asset',
    'search_knowledge_base'
]
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_crm_agent'
  AND status = 'ACTIVE';

UPDATE agent_registry_entries
SET allowed_tools = ARRAY[
    'get_stock',
    'search_inventory',
    'reserve_stock',
    'search_knowledge_base'
]
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_inventory_agent'
  AND status = 'ACTIVE';

UPDATE agent_registry_entries
SET allowed_tools = ARRAY['lookup_customer', 'list_customers', 'search_customer_orders']
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_live_crm_agent'
  AND status = 'ACTIVE';

UPDATE agent_registry_entries
SET allowed_tools = ARRAY['get_stock', 'search_inventory']
WHERE tenant_id = 'demo-tenant'
  AND agent_id = 'vextis_live_inventory_agent'
  AND status = 'ACTIVE';
