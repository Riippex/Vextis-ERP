package com.vextis.crm;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProposalAssetDirectory {

    List<ProposalAssetView> findByQuoteId(String tenantId, String quoteId);

    List<ProposalAssetView> findByQuoteId(String tenantId, String quoteId, int limit);

    List<ProposalAssetView> findAll(String tenantId);

    Optional<ProposalAssetView> findById(String tenantId, UUID assetId);

    ProposalAssetView registerAsset(RegisterProposalAssetCommand command);

    record RegisterProposalAssetCommand(
            String tenantId,
            String quoteId,
            String storageUri,
            Long storageGeneration,
            String contentType,
            String contentHash,
            Long sizeBytes,
            MediaType mediaType,
            String modelId,
            String promptSummary,
            String aiLabel,
            String actorType,
            String actorId,
            String correlationId,
            String idempotencyKey
    ) {
        public RegisterProposalAssetCommand(
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
            this(tenantId, quoteId, storageUri, null, null, null, null,
                    mediaType, modelId, promptSummary, aiLabel, actorType, actorId, correlationId, idempotencyKey);
        }
    }

    record ProposalAssetView(
            UUID id,
            String quoteId,
            String storageUri,
            Long storageGeneration,
            String contentType,
            String contentHash,
            Long sizeBytes,
            MediaType mediaType,
            String modelId,
            String promptSummary,
            String aiLabel,
            String actorType,
            String actorId,
            String correlationId,
            Instant createdAt
    ) {
        public ProposalAssetView(
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
            this(id, quoteId, storageUri, null, null, null, null,
                    mediaType, modelId, promptSummary, aiLabel, actorType, actorId, correlationId, createdAt);
        }
    }

    enum MediaType {
        IMAGE,
        VIDEO
    }
}
