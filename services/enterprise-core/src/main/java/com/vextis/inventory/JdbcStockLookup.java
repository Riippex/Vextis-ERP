package com.vextis.inventory;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
class JdbcStockLookup implements StockLookup {
    private final NamedParameterJdbcTemplate jdbc;

    JdbcStockLookup(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StockSnapshot> findBySku(String tenantId, String sku) {
        return jdbc.query(
                "SELECT sku, available_quantity FROM inventory_stock WHERE tenant_id = :tenantId AND sku = :sku",
                Map.of("tenantId", tenantId, "sku", sku),
                (rs, row) -> new StockSnapshot(rs.getString("sku"), rs.getInt("available_quantity"))
        ).stream().findFirst();
    }
}
