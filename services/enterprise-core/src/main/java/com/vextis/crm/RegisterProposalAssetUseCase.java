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
            ProposalAssetDirectory.ReservationStatus status,
            boolean owner,
            String reservationToken,
            boolean alreadyRegistered,
            ProposalAssetDirectory.ProposalAssetView existingAsset
    ) {
        public PreflightResult(UUID quoteId, String tenantPrefix, String correlationId, boolean authorized) {
            this(quoteId, tenantPrefix, correlationId, authorized, ProposalAssetDirectory.ReservationStatus.RESERVED, true, null, false, null);
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
            String aiLabel,
            String reservationToken
    ) {
        public RegisterCommand(
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
            this(tenantId, agentId, quoteId, correlationId, idempotencyKey, storageUri, mediaType, modelId, promptSummary, aiLabel, null);
        }
    }
}
