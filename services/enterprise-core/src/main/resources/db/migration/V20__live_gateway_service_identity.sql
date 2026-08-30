-- Registry entries bound to the Live gateway service identity.
--
-- The publicly reachable Live gateway now presents its own credential, which
-- Enterprise Core resolves to the service identity 'live-gateway-agent'. That
-- separation is only worth something if the identity can do less: these entries
-- are what a voice session may reach, and they are read-only.
--
-- A voice session can look up a customer, check stock, check credit standing
-- and search the knowledge base. It cannot start planning, record a plan,
-- request approval, reserve stock, issue an invoice or write to the knowledge
-- base. Those stay with 'coordinator-agent', whose credential lives only on the
-- private Agent Runtime.
--
-- Distinct agent ids are required rather than reusing the existing ones:
-- ux_agent_registry_one_active_version allows a single ACTIVE row per
-- (tenant_id, agent_id), so the same logical agent cannot hold two identities.

INSERT INTO agent_registry_entries (
    tenant_id, agent_id, version, display_name, department, purpose, framework,
    model_id, prompt_version, service_identity, status, capabilities, allowed_tools
) VALUES
    (
        'demo-tenant', 'vextis_live_coordinator', '1.0.0', 'Vextis Live Coordinator',
        'CROSS_DEPARTMENT', 'Coordinates a spoken Ask Vextis session over read-only context.',
        'GOOGLE_ADK', 'gemini-live-2.5-flash-native-audio', '1.0.0', 'live-gateway-agent', 'ACTIVE',
        ARRAY['voice coordination', 'knowledge retrieval'],
        ARRAY['search_knowledge_base']
    ),
    (
        'demo-tenant', 'vextis_live_crm_agent', '1.0.0', 'Live CRM & Sales Agent',
        'CRM_SALES', 'Reads authoritative customer context during a voice session.',
        'GOOGLE_ADK', 'gemini-live-2.5-flash-native-audio', '1.0.0', 'live-gateway-agent', 'ACTIVE',
        ARRAY['customer lookup'],
        ARRAY['lookup_customer']
    ),
    (
        'demo-tenant', 'vextis_live_inventory_agent', '1.0.0', 'Live Inventory Agent',
        'INVENTORY_OPERATIONS', 'Reads authoritative SKU availability during a voice session.',
        'GOOGLE_ADK', 'gemini-live-2.5-flash-native-audio', '1.0.0', 'live-gateway-agent', 'ACTIVE',
        ARRAY['stock lookup'],
        ARRAY['get_stock']
    ),
    (
        'demo-tenant', 'vextis_live_billing_agent', '1.0.0', 'Live Finance & Billing Agent',
        'FINANCE_BILLING', 'Reads authoritative credit standing during a voice session.',
        'GOOGLE_ADK', 'gemini-live-2.5-flash-native-audio', '1.0.0', 'live-gateway-agent', 'ACTIVE',
        ARRAY['credit lookup'],
        ARRAY['get_credit']
    );
