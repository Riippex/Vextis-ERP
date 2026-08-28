package com.vextis.workflow.api.internal;

import com.vextis.crm.ProposalAssetDirectory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/internal/agent-tools/v1/crm/quotes")
class AgentProposalAssetController {

    private final ProposalAssetDirectory proposalAssets;
    private final AgentToolAuthorizer authorizer;

    AgentProposalAssetController(ProposalAssetDirectory proposalAssets, AgentToolAuthorizer authorizer) {
        this.proposalAssets = proposalAssets;
        this.authorizer = authorizer;
    }

    @PostMapping("/{quoteId}/assets")
    @ResponseStatus(HttpStatus.CREATED)
    ProposalAssetResponse registerAsset(
            @PathVariable @NotBlank @Size(max = 100) String quoteId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 16, max = 200) String idempotencyKey,
            @RequestBody @Valid RegisterProposalAssetRequest request
    ) {
        authorizer.authorize(authorization, agentId, tenantId, AgentTool.REGISTER_QUOTE_ASSET);

        ProposalAssetDirectory.MediaType mediaType = ProposalAssetDirectory.MediaType.valueOf(request.mediaType());
        ProposalAssetDirectory.ProposalAssetView view = proposalAssets.registerAsset(
                new ProposalAssetDirectory.RegisterProposalAssetCommand(
                        tenantId,
                        quoteId,
                        request.storageUri(),
                        mediaType,
                        request.modelId(),
                        request.promptSummary(),
                        request.aiLabel(),
                        "AGENT",
                        agentId,
                        correlationId,
                        idempotencyKey
                )
        );

        return ProposalAssetResponse.from(view);
    }

    record RegisterProposalAssetRequest(
            @NotBlank @Size(max = 1000) @Pattern(regexp = "^(gs://|https://|urn:).+") String storageUri,
            @NotBlank @Pattern(regexp = "^(IMAGE|VIDEO)$") String mediaType,
            @NotBlank @Size(max = 150) String modelId,
            @NotBlank @Size(max = 500) String promptSummary,
            @NotBlank @Size(max = 100) String aiLabel
    ) {
    }

    record ProposalAssetResponse(
            UUID id,
            String quoteId,
            String storageUri,
            String mediaType,
            String modelId,
            String promptSummary,
            String aiLabel,
            String createdAt
    ) {
        static ProposalAssetResponse from(ProposalAssetDirectory.ProposalAssetView view) {
            return new ProposalAssetResponse(
                    view.id(),
                    view.quoteId(),
                    view.storageUri(),
                    view.mediaType().name(),
                    view.modelId(),
                    view.promptSummary(),
                    view.aiLabel(),
                    view.createdAt().toString()
            );
        }
    }
}
