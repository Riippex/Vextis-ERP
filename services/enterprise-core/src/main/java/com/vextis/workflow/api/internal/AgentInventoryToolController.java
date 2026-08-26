package com.vextis.workflow.api.internal;

import com.vextis.inventory.StockReservation;
import com.vextis.workflow.application.ReserveApprovedStockCommand;
import com.vextis.workflow.application.ReserveApprovedStockUseCase;
import com.vextis.workflow.application.WorkflowConflictException;
import com.vextis.workflow.application.WorkflowNotFoundException;
import com.vextis.workflow.domain.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/internal/agent-tools/v1/inventory/reservations")
class AgentInventoryToolController {

    private final ReserveApprovedStockUseCase reserveStock;
    private final AgentToolAuthorizer authorizer;

    AgentInventoryToolController(ReserveApprovedStockUseCase reserveStock, AgentToolAuthorizer authorizer) {
        this.reserveStock = reserveStock;
        this.authorizer = authorizer;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ReservationResponse reserve(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 16, max = 200) String idempotencyKey,
            @RequestBody @Valid ReserveStockRequest request
    ) {
        authorizer.authorize(authorization, agentId, tenantId, AgentTool.RESERVE_STOCK);
        try {
            return ReservationResponse.from(reserveStock.reserve(new ReserveApprovedStockCommand(
                    tenantId, new Actor(Actor.Type.AGENT, agentId), request.orderId(), request.sku(),
                    request.quantity(), correlationId, idempotencyKey)));
        } catch (WorkflowNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (WorkflowConflictException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    record ReserveStockRequest(
            @NotNull UUID orderId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String sku,
            @Min(1) @Max(1_000_000) int quantity
    ) {
    }

    record ReservationResponse(UUID id, UUID orderId, String sku, int quantity, String status, String createdAt) {
        static ReservationResponse from(StockReservation.Reservation reservation) {
            return new ReservationResponse(
                    reservation.id(), reservation.orderId(), reservation.sku(), reservation.quantity(),
                    reservation.status().name(), reservation.createdAt().toString());
        }
    }
}
