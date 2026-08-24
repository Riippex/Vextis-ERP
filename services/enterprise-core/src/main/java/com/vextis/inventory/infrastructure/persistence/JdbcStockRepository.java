package com.vextis.inventory.infrastructure.persistence;

import com.vextis.inventory.StockDirectory;
import com.vextis.inventory.application.port.StockRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStockRepository implements StockRepository {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcStockRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StockDirectory.StockSummary setAvailability(String tenantId, String sku, int availableQuantity) {
        jdbc.update(
                """
                INSERT INTO inventory_stock (tenant_id, sku, available_quantity)
                VALUES (:tenantId, :sku, :availableQuantity)
                ON CONFLICT (tenant_id, sku)
                DO UPDATE SET available_quantity = EXCLUDED.available_quantity
                """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("sku", sku)
                        .addValue("availableQuantity", availableQuantity)
        );
        return new StockDirectory.StockSummary(sku, availableQuantity);
    }
}
