package com.vextis.crm;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProposalAssetDirectory {

    List<ProposalAssetView> findByQuoteId(String tenantId, String quoteId);

    List<ProposalAssetView> findAll(String tenantId);

    Optional<ProposalAssetView> findById(String tenantId, UUID assetId);

    ProposalAssetView registerAsset(RegisterProposalAssetCommand command);

    record RegisterProposalAssetCommand(
            String tenantId,
            String quoteId,
            String storageUri,
            MediaType mediaType,
            String modelId,
            String promptSummary,
            String aiLabel,
            String actorType,
            String actorId,
            String correlationId,
            String idempotencyKey
    ) {
    }

    record ProposalAssetView(
            UUID id,
            String quoteId,
            String storageUri,
            MediaType mediaType,
            String modelId,
            String promptSummary,
            String aiLabel,
            String actorType,
            String actorId,
            String correlationId,
            Instant createdAt
    ) {
    }

    enum MediaType {
        IMAGE,
        VIDEO
    }
}
