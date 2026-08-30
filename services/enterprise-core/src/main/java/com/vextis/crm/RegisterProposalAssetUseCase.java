package com.vextis.crm;

import java.util.UUID;

public interface RegisterProposalAssetUseCase {

    PreflightResult preflight(PreflightCommand command);

    ProposalAssetDirectory.ProposalAssetView registerAsset(RegisterCommand command);

    record PreflightCommand(
            String tenantId,
            String agentId,
            UUID quoteId,
            String correlationId
    ) {
    }

    record PreflightResult(
            UUID quoteId,
            String tenantPrefix,
            String correlationId,
            boolean authorized
    ) {
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
