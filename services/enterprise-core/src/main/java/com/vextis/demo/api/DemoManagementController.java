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

@RestController
@RequestMapping("/internal/demo")
public class DemoManagementController {

    private final DemoSeedingService demoSeedingService;
    private final DemoResetService demoResetService;
    private final String serviceToken;
    private final String defaultTenantId;

    public DemoManagementController(
            DemoSeedingService demoSeedingService,
            DemoResetService demoResetService,
            @Value("${vextis.agent-tools.service-token:}") String serviceToken,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String defaultTenantId
    ) {
        this.demoSeedingService = demoSeedingService;
        this.demoResetService = demoResetService;
        this.serviceToken = serviceToken;
        this.defaultTenantId = defaultTenantId;
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
        authorizeServiceToken(authorization);

        DemoSeedingService.SeedResult result =
                demoSeedingService.seedDemoData(tenantIdOf(request), actorIdOf(request));

        return ResponseEntity.ok(SeedDemoResponse.from("SEEDED", result));
    }

    @PostMapping("/reset")
    public ResponseEntity<ResetDemoResponse> reset(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) SeedDemoRequest request
    ) {
        authorizeServiceToken(authorization);

        DemoResetService.ResetResult result =
                demoResetService.resetDemoData(tenantIdOf(request), actorIdOf(request));
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

    private String tenantIdOf(SeedDemoRequest request) {
        return (request != null && request.tenantId() != null) ? request.tenantId() : defaultTenantId;
    }

    private String actorIdOf(SeedDemoRequest request) {
        return (request != null && request.actorId() != null) ? request.actorId() : "demo-admin";
    }

    private void authorizeServiceToken(String authorization) {
        if (serviceToken.isBlank()) {
            return; // In local development without configured token, allow seeding for developer convenience
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing service credential");
        }
        byte[] presented = authorization.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
        byte[] expected = serviceToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(presented, expected)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service credential");
        }
    }
}
