ALTER TABLE inventory_stock
    ADD COLUMN reserved_quantity INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_inventory_reserved_quantity CHECK (reserved_quantity >= 0);

CREATE TABLE inventory_reservations (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    order_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    actor_id VARCHAR(150) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_inventory_reservation_line UNIQUE (tenant_id, order_id, sku),
    CONSTRAINT uq_inventory_reservation_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_inventory_reservation_quantity CHECK (quantity BETWEEN 1 AND 1000000),
    CONSTRAINT ck_inventory_reservation_status CHECK (status IN ('RESERVED', 'RELEASED', 'FULFILLED'))
);

CREATE INDEX idx_inventory_reservations_tenant_created
    ON inventory_reservations (tenant_id, created_at DESC);
