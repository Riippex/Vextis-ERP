package com.vextis.missioncontrol.api.graphql;

import com.vextis.billing.CreditPortfolio;
import com.vextis.crm.CustomerDirectory;
import com.vextis.inventory.StockDirectory;
import com.vextis.inventory.ReservationDirectory;
import com.vextis.inventory.StockReservation;
import com.vextis.workflow.ExecutionOverview;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
class MissionControlGraphQlController {

    private static final int RECENT_EXECUTION_LIMIT = 12;

    private final ExecutionOverview executions;
    private final CustomerDirectory customers;
    private final StockDirectory stock;
    private final ReservationDirectory reservations;
    private final CreditPortfolio credit;
    private final String demoTenantId;

    MissionControlGraphQlController(
            ExecutionOverview executions,
            CustomerDirectory customers,
            StockDirectory stock,
            ReservationDirectory reservations,
            CreditPortfolio credit,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String demoTenantId
    ) {
        this.executions = executions;
        this.customers = customers;
        this.stock = stock;
        this.reservations = reservations;
        this.credit = credit;
        this.demoTenantId = demoTenantId;
    }

    @QueryMapping
    MissionControlView missionControl() {
        List<CustomerDirectory.CustomerSummary> customerSnapshots = customers.findAll(demoTenantId);
        Map<UUID, CustomerDirectory.CustomerSummary> customersById = customerSnapshots.stream()
                .collect(Collectors.toMap(CustomerDirectory.CustomerSummary::id, Function.identity()));

        return new MissionControlView(
                executions.findRecent(demoTenantId, RECENT_EXECUTION_LIMIT).stream()
                        .map(MissionControlExecutionView::from).toList(),
                customerSnapshots.stream().map(CustomerOverviewView::from).toList(),
                stock.findAll(demoTenantId).stream().map(StockItemOverviewView::from).toList(),
                reservations.findAll(demoTenantId).stream().map(StockReservationOverviewView::from).toList(),
                credit.findAll(demoTenantId).stream()
                        .map(profile -> CreditProfileOverviewView.from(profile, customersById.get(profile.customerId())))
                        .toList(),
                executions.volumeByDepartment(demoTenantId).stream()
                        .map(DepartmentExecutionVolumeView::from).toList()
        );
    }

    record MissionControlView(
            List<MissionControlExecutionView> executions,
            List<CustomerOverviewView> customers,
            List<StockItemOverviewView> stockItems,
            List<StockReservationOverviewView> stockReservations,
            List<CreditProfileOverviewView> creditProfiles,
            List<DepartmentExecutionVolumeView> executionVolumeByDepartment
    ) {
    }

    record MissionControlExecutionView(
            UUID id,
            String purchaseOrderNumber,
            String customerName,
            String state,
            String correlationId,
            String updatedAt
    ) {
        static MissionControlExecutionView from(ExecutionOverview.ExecutionSummary summary) {
            return new MissionControlExecutionView(
                    summary.id(), summary.purchaseOrderNumber(), summary.customerName(), summary.state(),
                    summary.correlationId(), summary.updatedAt().toString());
        }
    }

    record CustomerOverviewView(UUID id, String legalName, boolean active) {
        static CustomerOverviewView from(CustomerDirectory.CustomerSummary customer) {
            return new CustomerOverviewView(customer.id(), customer.legalName(), customer.active());
        }
    }

    record StockItemOverviewView(String sku, int availableQuantity) {
        static StockItemOverviewView from(StockDirectory.StockSummary item) {
            return new StockItemOverviewView(item.sku(), item.availableQuantity());
        }
    }

    record StockReservationOverviewView(
            UUID id, UUID orderId, String sku, int quantity, String status, String createdAt
    ) {
        static StockReservationOverviewView from(StockReservation.Reservation reservation) {
            return new StockReservationOverviewView(
                    reservation.id(), reservation.orderId(), reservation.sku(), reservation.quantity(),
                    reservation.status().name(), reservation.createdAt().toString());
        }
    }

    record CreditProfileOverviewView(
            UUID customerId,
            String customerName,
            String standing,
            int maxPaymentTermsDays
    ) {
        static CreditProfileOverviewView from(
                CreditPortfolio.CreditProfileSummary profile,
                CustomerDirectory.CustomerSummary customer
        ) {
            String customerName = customer == null ? "Unknown customer" : customer.legalName();
            return new CreditProfileOverviewView(
                    profile.customerId(), customerName, profile.standing(), profile.maxPaymentTermsDays());
        }
    }

    record DepartmentExecutionVolumeView(String department, int count) {
        static DepartmentExecutionVolumeView from(ExecutionOverview.DepartmentVolume volume) {
            return new DepartmentExecutionVolumeView(volume.department(), volume.count());
        }
    }
}
