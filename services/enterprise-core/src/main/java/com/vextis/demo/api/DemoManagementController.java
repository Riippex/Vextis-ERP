package com.vextis.demo.api;

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
    private final String serviceToken;
    private final String defaultTenantId;

    public DemoManagementController(
            DemoSeedingService demoSeedingService,
            @Value("${vextis.agent-tools.service-token:}") String serviceToken,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String defaultTenantId
    ) {
        this.demoSeedingService = demoSeedingService;
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
    }

    @PostMapping("/seed")
    public ResponseEntity<SeedDemoResponse> seed(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) SeedDemoRequest request
    ) {
        authorizeServiceToken(authorization);

        String tenantId = (request != null && request.tenantId() != null) ? request.tenantId() : defaultTenantId;
        String actorId = (request != null && request.actorId() != null) ? request.actorId() : "demo-admin";

        DemoSeedingService.SeedResult result = demoSeedingService.seedDemoData(tenantId, actorId);

        return ResponseEntity.ok(new SeedDemoResponse(
                "SEEDED",
                result.tenantId(),
                result.customers().size(),
                result.creditProfiles().size(),
                result.inventory().size(),
                result.knowledgeDocuments().size()
        ));
    }

    @PostMapping("/reset")
    public ResponseEntity<SeedDemoResponse> reset(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) SeedDemoRequest request
    ) {
        return seed(authorization, request);
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
