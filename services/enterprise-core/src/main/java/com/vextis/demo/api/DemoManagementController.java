package com.vextis.demo.api;

import com.vextis.demo.DemoResetService;
import com.vextis.demo.DemoSeedingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Demo administration: seeding and the destructive tenant reset.
 *
 * <p>These are not agent tools. They used to accept the shared
 * {@code vextis.agent-tools.service-token}, which meant the credential Agent
 * Runtime carries for business reads could purge a tenant, and they accepted
 * whatever {@code tenantId} the body carried, so that purge was not even
 * confined to the demo tenant. Both are closed here: a separate administrative
 * credential, and the configured demo tenant as the only permitted target.
 */
@RestController
@RequestMapping("/internal/demo")
public class DemoManagementController {

    private final DemoSeedingService demoSeedingService;
    private final DemoResetService demoResetService;
    private final String adminToken;
    private final String demoTenantId;
    private final boolean localExposure;

    public DemoManagementController(
            DemoSeedingService demoSeedingService,
            DemoResetService demoResetService,
            @Value("${vextis.demo.admin-token:}") String adminToken,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String demoTenantId,
            @Value("${vextis.exposure:INTERNAL}") String exposure
    ) {
        this.demoSeedingService = demoSeedingService;
        this.demoResetService = demoResetService;
        this.adminToken = adminToken;
        this.demoTenantId = demoTenantId;
        this.localExposure = "LOCAL".equalsIgnoreCase(exposure);
    }

    public record SeedDemoRequest(String tenantId, String actorId) {
    }

    public record SeedDemoResponse(
            String status,
            String tenantId,
            int customersCount,
            int creditProfilesCount,
            int inventorySkusCount,
            int knowledgeDocumentsCount
    ) {
        static SeedDemoResponse from(String status, DemoSeedingService.SeedResult result) {
            return new SeedDemoResponse(
                    status,
                    result.tenantId(),
                    result.customers().size(),
                    result.creditProfiles().size(),
                    result.inventory().size(),
                    result.knowledgeDocuments().size()
            );
        }
    }

    /**
     * Reset reports what it removed as well as what it seeded, so a caller can
     * tell an actual reset from a re-seed that left previous demo state behind.
     */
    public record ResetDemoResponse(
            String status,
            String tenantId,
            int purgedRowsTotal,
            Map<String, Integer> purgedRowsByArea,
            int customersCount,
            int creditProfilesCount,
            int inventorySkusCount,
            int knowledgeDocumentsCount
    ) {
    }

    @PostMapping("/seed")
    public ResponseEntity<SeedDemoResponse> seed(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) SeedDemoRequest request
    ) {
        authorizeAdministrator(authorization);
        String tenantId = requireDemoTenant(request);

        DemoSeedingService.SeedResult result =
                demoSeedingService.seedDemoData(tenantId, actorIdOf(request));

        return ResponseEntity.ok(SeedDemoResponse.from("SEEDED", result));
    }

    @PostMapping("/reset")
    public ResponseEntity<ResetDemoResponse> reset(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) SeedDemoRequest request
    ) {
        authorizeAdministrator(authorization);
        String tenantId = requireDemoTenant(request);

        DemoResetService.ResetResult result =
                demoResetService.resetDemoData(tenantId, actorIdOf(request));
        DemoSeedingService.SeedResult seed = result.seed();

        return ResponseEntity.ok(new ResetDemoResponse(
                "RESET",
                seed.tenantId(),
                result.purgedRowsTotal(),
                result.purgedRowsByArea(),
                seed.customers().size(),
                seed.creditProfiles().size(),
                seed.inventory().size(),
                seed.knowledgeDocuments().size()
        ));
    }

    /**
     * Only the configured demo tenant may be seeded or purged. Redirecting a
     * foreign tenant to the demo one silently would be worse than refusing:
     * the caller asked to destroy something else.
     */
    private String requireDemoTenant(SeedDemoRequest request) {
        if (request == null || request.tenantId() == null || request.tenantId().isBlank()) {
            return demoTenantId;
        }
        String requested = request.tenantId().trim();
        if (!demoTenantId.equals(requested)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Demo administration is limited to the configured demo tenant");
        }
        return requested;
    }

    private String actorIdOf(SeedDemoRequest request) {
        return (request != null && request.actorId() != null) ? request.actorId() : "demo-admin";
    }

    private void authorizeAdministrator(String authorization) {
        if (adminToken.isBlank()) {
            if (localExposure) {
                // A developer running the stack locally has no secret manager.
                return;
            }
            // Fail closed. Skipping the check when unconfigured left seeding and
            // the destructive reset open to anyone who could reach the service.
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Demo administration is disabled until VEXTIS_DEMO_ADMIN_TOKEN is configured");
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing administrative credential");
        }
        byte[] presented = authorization.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
        byte[] expected = adminToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(presented, expected)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid administrative credential");
        }
    }
}
