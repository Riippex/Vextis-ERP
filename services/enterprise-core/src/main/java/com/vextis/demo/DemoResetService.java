package com.vextis.demo;

import com.vextis.shared.TenantDataPurge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns a tenant to the state a fresh seed produces.
 *
 * <p>Reset previously just called the seeder again, which left every row a demo
 * run had created — reservations drawing down stock, issued invoices, completed
 * executions, idempotency records that make the next run replay a stored
 * response — so a second demo did not behave like the first. It now removes the
 * tenant rows each module owns, in dependency order, and seeds afterwards
 * inside one transaction.
 *
 * <p>The audit trail is deliberately not purged.
 */
@Service
@Transactional
public class DemoResetService {

    private static final Logger log = LoggerFactory.getLogger(DemoResetService.class);

    private final String defaultTenantId;
    private final List<TenantDataPurge> purges;
    private final DemoSeedingService seedingService;

    public DemoResetService(
            @Value("${vextis.demo.tenant-id:demo-tenant}") String defaultTenantId,
            List<TenantDataPurge> purges,
            DemoSeedingService seedingService
    ) {
        this.defaultTenantId = defaultTenantId;
        this.purges = purges.stream()
                .sorted(Comparator.comparingInt(TenantDataPurge::order))
                .toList();
        this.seedingService = seedingService;
    }

    public record ResetResult(
            Map<String, Integer> purgedRowsByArea,
            int purgedRowsTotal,
            DemoSeedingService.SeedResult seed
    ) {
    }

    public ResetResult resetDemoData(String tenantId, String actorId) {
        String effectiveTenant = (tenantId == null || tenantId.isBlank()) ? defaultTenantId : tenantId.trim();

        Map<String, Integer> purged = new LinkedHashMap<>();
        int total = 0;
        for (TenantDataPurge purge : purges) {
            int removed = purge.purgeTenant(effectiveTenant);
            purged.put(purge.area(), removed);
            total += removed;
        }

        log.info("Purged {} rows across {} areas for tenant={}", total, purged.size(), effectiveTenant);

        DemoSeedingService.SeedResult seed = seedingService.seedDemoData(effectiveTenant, actorId);
        return new ResetResult(Map.copyOf(purged), total, seed);
    }
}
