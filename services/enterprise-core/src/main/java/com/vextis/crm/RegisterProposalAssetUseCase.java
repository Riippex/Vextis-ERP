package com.vextis.crm;

import java.util.UUID;

public interface RegisterProposalAssetUseCase {

    PreflightResult preflight(PreflightCommand command);

    ProposalAssetDirectory.ProposalAssetView registerAsset(RegisterCommand command);

    record PreflightCommand(
            String tenantId,
            String agentId,
            UUID quoteId,
            String correlationId,
            String idempotencyKey,
            String promptSummary
    ) {
        public PreflightCommand(String tenantId, String agentId, UUID quoteId, String correlationId) {
            this(tenantId, agentId, quoteId, correlationId, null, null);
        }
    }

    record PreflightResult(
            UUID quoteId,
            String tenantPrefix,
            String correlationId,
            boolean authorized,
            boolean alreadyRegistered,
            ProposalAssetDirectory.ProposalAssetView existingAsset
    ) {
        public PreflightResult(UUID quoteId, String tenantPrefix, String correlationId, boolean authorized) {
            this(quoteId, tenantPrefix, correlationId, authorized, false, null);
        }
    }

    record RegisterCommand(
            String tenantId,
            String agentId,
            UUID quoteId,
            String correlationId,
            String idempotencyKey,
            String storageUri,
            ProposalAssetDirectory.MediaType mediaType,
            String modelId,
            String promptSummary,
            String aiLabel
    ) {
    }
}
