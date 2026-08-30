package com.vextis.workflow.api.internal;

import com.vextis.crm.ProposalAssetConflictException;
import com.vextis.crm.ProposalAssetDirectory;
import com.vextis.crm.RegisterProposalAssetUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/internal/agent-tools/v1/crm/quotes")
class AgentProposalAssetController {

    private final RegisterProposalAssetUseCase proposalAssetUseCase;
    private final AgentToolAuthorizer authorizer;

    AgentProposalAssetController(
            RegisterProposalAssetUseCase proposalAssetUseCase,
            AgentToolAuthorizer authorizer
    ) {
        this.proposalAssetUseCase = proposalAssetUseCase;
        this.authorizer = authorizer;
    }

    @PostMapping("/{quoteId}/assets/preflight")
    @ResponseStatus(HttpStatus.OK)
    PreflightProposalAssetResponse preflight(
            @PathVariable UUID quoteId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) PreflightProposalAssetRequest request
    ) {
        authorizer.authorize(authorization, agentId, tenantId, AgentTool.REGISTER_QUOTE_ASSET);

        String promptSummary = request != null ? request.promptSummary() : null;

        try {
            RegisterProposalAssetUseCase.PreflightResult result = proposalAssetUseCase.preflight(
                    new RegisterProposalAssetUseCase.PreflightCommand(
                            tenantId,
                            agentId,
                            quoteId,
                            correlationId,
                            idempotencyKey,
                            promptSummary
                    )
            );
            return new PreflightProposalAssetResponse(
                    result.quoteId().toString(),
                    result.authorized(),
                    result.tenantPrefix(),
                    result.correlationId(),
                    result.status().name(),
                    result.owner(),
                    result.alreadyRegistered(),
                    result.existingAsset() != null ? ProposalAssetResponse.from(result.existingAsset()) : null
            );
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), exception);
        } catch (ProposalAssetConflictException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/{quoteId}/assets")
    @ResponseStatus(HttpStatus.CREATED)
    ProposalAssetResponse registerAsset(
            @PathVariable UUID quoteId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 16, max = 200) String idempotencyKey,
            @RequestBody @Valid RegisterProposalAssetRequest request
    ) {
        authorizer.authorize(authorization, agentId, tenantId, AgentTool.REGISTER_QUOTE_ASSET);

        ProposalAssetDirectory.MediaType mediaType = ProposalAssetDirectory.MediaType.valueOf(request.mediaType());
        try {
            ProposalAssetDirectory.ProposalAssetView view = proposalAssetUseCase.registerAsset(
                    new RegisterProposalAssetUseCase.RegisterCommand(
                            tenantId,
                            agentId,
                            quoteId,
                            correlationId,
                            idempotencyKey,
                            request.storageUri(),
                            mediaType,
                            request.modelId(),
                            request.promptSummary(),
                            request.aiLabel()
                    )
            );
            return ProposalAssetResponse.from(view);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), exception);
        } catch (ProposalAssetConflictException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    record PreflightProposalAssetRequest(
            @Size(max = 500) String promptSummary
    ) {
    }

    record PreflightProposalAssetResponse(
            String quoteId,
            boolean authorized,
            String tenantPrefix,
            String correlationId,
            String status,
            boolean owner,
            boolean alreadyRegistered,
            ProposalAssetResponse existingAsset
    ) {
    }

    record RegisterProposalAssetRequest(
            @NotBlank @Size(max = 1000) @Pattern(regexp = "^gs://.+") String storageUri,
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
