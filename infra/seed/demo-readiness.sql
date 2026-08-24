INSERT INTO crm_customers (id, tenant_id, legal_name, active)
VALUES ('11111111-1111-1111-1111-111111111111', 'demo-tenant', 'Acme Colombia', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO inventory_stock (tenant_id, sku, available_quantity)
VALUES
    ('demo-tenant', 'VXT-CHAIR-01', 40),
    ('demo-tenant', 'VXT-DESK-01', 12)
ON CONFLICT DO NOTHING;

INSERT INTO billing_credit_profiles (tenant_id, customer_id, standing, max_payment_terms_days)
VALUES ('demo-tenant', '11111111-1111-1111-1111-111111111111', 'GOOD', 30)
ON CONFLICT DO NOTHING;
