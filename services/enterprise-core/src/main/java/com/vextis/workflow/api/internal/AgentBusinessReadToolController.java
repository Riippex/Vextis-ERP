package com.vextis.workflow.api.internal;

import com.vextis.billing.CreditLookup;
import com.vextis.crm.CustomerLookup;
import com.vextis.crm.CustomerDirectory;
import com.vextis.inventory.StockLookup;
import com.vextis.inventory.StockDirectory;
import com.vextis.workflow.ExecutionOverview;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.List;
import java.util.Locale;

@RestController
@Validated
class AgentBusinessReadToolController {

    private final CustomerLookup customers;
    private final StockLookup stock;
    private final CreditLookup credit;
    private final CustomerDirectory customerDirectory;
    private final StockDirectory stockDirectory;
    private final ExecutionOverview executions;
    private final AgentToolAuthorizer authorizer;

    AgentBusinessReadToolController(
            CustomerLookup customers,
            StockLookup stock,
            CreditLookup credit,
            CustomerDirectory customerDirectory,
            StockDirectory stockDirectory,
            ExecutionOverview executions,
            AgentToolAuthorizer authorizer
    ) {
        this.customers = customers;
        this.stock = stock;
        this.credit = credit;
        this.customerDirectory = customerDirectory;
        this.stockDirectory = stockDirectory;
        this.executions = executions;
        this.authorizer = authorizer;
    }

    @GetMapping("/internal/agent-tools/v1/crm/customers")
    List<CustomerResponse> listCustomers(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit
    ) {
        authorizer.authorize(authorization, agentId, tenantId, AgentTool.LIST_CUSTOMERS);
        return customerDirectory.findAll(tenantId).stream()
                .sorted(java.util.Comparator.comparing(CustomerDirectory.CustomerSummary::legalName,
                        String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(CustomerResponse::from)
                .toList();
    }

    @GetMapping("/internal/agent-tools/v1/crm/customers/orders")
    CustomerOrdersResponse searchCustomerOrders(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @RequestParam @NotBlank @Size(max = 200) String legalName,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit
    ) {
        authorizer.authorize(authorization, agentId, tenantId, AgentTool.SEARCH_CUSTOMER_ORDERS);
        return CustomerOrdersResponse.from(executions.findCustomerOrders(tenantId, legalName, limit));
    }

    @GetMapping("/internal/agent-tools/v1/crm/customers/lookup")
    CustomerResponse lookupCustomer(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @RequestParam @NotBlank @Size(max = 200) String legalName
    ) {
        authorizer.authorize(authorization, agentId, tenantId, AgentTool.LOOKUP_CUSTOMER);
        return customers.findByLegalName(tenantId, legalName)
                .map(CustomerResponse::from)
                .orElseThrow(() -> notFound("Customer was not found"));
    }

    @GetMapping("/internal/agent-tools/v1/inventory/stock/{sku}")
    StockResponse getStock(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @PathVariable @NotBlank @Size(max = 100)
            @Pattern(regexp = "^[A-Za-z0-9._-]+$") String sku
    ) {
        authorizer.authorize(authorization, agentId, tenantId, AgentTool.GET_STOCK);
        return stock.findBySku(tenantId, sku)
                .map(StockResponse::from)
                .orElseThrow(() -> notFound("SKU was not found"));
    }

    @GetMapping("/internal/agent-tools/v1/inventory/stock")
    List<StockResponse> searchInventory(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @RequestParam(defaultValue = "") @Size(max = 100) String query,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit
    ) {
        authorizer.authorize(authorization, agentId, tenantId, AgentTool.SEARCH_INVENTORY);
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        return stockDirectory.findAll(tenantId).stream()
                .filter(item -> normalizedQuery.isEmpty()
                        || item.sku().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(java.util.Comparator.comparing(StockDirectory.StockSummary::sku))
                .limit(limit)
                .map(StockResponse::from)
                .toList();
    }

    @GetMapping("/internal/agent-tools/v1/billing/customers/{customerId}/credit")
    CreditResponse getCredit(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 100) String tenantId,
            @RequestHeader("X-Agent-Id") @NotBlank @Size(max = 150) String agentId,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 100) String correlationId,
            @PathVariable UUID customerId
    ) {
        authorizer.authorize(authorization, agentId, tenantId, AgentTool.GET_CREDIT);
        return credit.findByCustomer(tenantId, customerId)
                .map(snapshot -> CreditResponse.from(customerId, snapshot))
                .orElseThrow(() -> notFound("Credit profile was not found"));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    record CustomerResponse(UUID id, String legalName, boolean active) {
        static CustomerResponse from(CustomerLookup.CustomerSnapshot customer) {
            return new CustomerResponse(customer.id(), customer.legalName(), customer.active());
        }

        static CustomerResponse from(CustomerDirectory.CustomerSummary customer) {
            return new CustomerResponse(customer.id(), customer.legalName(), customer.active());
        }
    }

    record StockResponse(String sku, int availableQuantity) {
        static StockResponse from(StockLookup.StockSnapshot stock) {
            return new StockResponse(stock.sku(), stock.availableQuantity());
        }

        static StockResponse from(StockDirectory.StockSummary stock) {
            return new StockResponse(stock.sku(), stock.availableQuantity());
        }
    }

    record CustomerOrderResponse(UUID id, String purchaseOrderNumber, String customerName,
                                 String state, String updatedAt) {
        static CustomerOrderResponse from(ExecutionOverview.ExecutionSummary order) {
            return new CustomerOrderResponse(order.id(), order.purchaseOrderNumber(), order.customerName(),
                    order.state(), order.updatedAt().toString());
        }
    }

    record CustomerOrdersResponse(int totalCount, List<CustomerOrderResponse> orders) {
        static CustomerOrdersResponse from(ExecutionOverview.CustomerOrders result) {
            return new CustomerOrdersResponse(
                    result.totalCount(), result.orders().stream().map(CustomerOrderResponse::from).toList());
        }
    }

    record CreditResponse(UUID customerId, String standing, int maxPaymentTermsDays) {
        static CreditResponse from(UUID customerId, CreditLookup.CreditSnapshot credit) {
            return new CreditResponse(customerId, credit.standing().name(), credit.maxPaymentTermsDays());
        }
    }
}
