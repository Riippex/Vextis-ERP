package com.vextis.demo;

import com.vextis.shared.TenantDataPurge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoResetServiceTests {

    private final DemoSeedingService seedingService = mock(DemoSeedingService.class);
    private final List<String> callLog = new ArrayList<>();

    private final class RecordingPurge implements TenantDataPurge {
        private final int order;
        private final String area;
        private final int rows;

        private RecordingPurge(int order, String area, int rows) {
            this.order = order;
            this.area = area;
            this.rows = rows;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public String area() {
            return area;
        }

        @Override
        public int purgeTenant(String tenantId) {
            callLog.add(area + ":" + tenantId);
            return rows;
        }
    }

    private DemoResetService newService(List<TenantDataPurge> purges) {
        when(seedingService.seedDemoData(any(), any())).thenAnswer(invocation ->
                new DemoSeedingService.SeedResult(
                        invocation.getArgument(0), List.of(), List.of(), List.of(), List.of()));
        return new DemoResetService("demo-tenant", purges, seedingService);
    }

    @Test
    void purgesEveryAreaInDependencyOrderBeforeSeeding() {
        DemoResetService service = newService(List.of(
                new RecordingPurge(70, "customers", 2),
                new RecordingPurge(10, "knowledge", 3),
                new RecordingPurge(40, "workflows", 5)
        ));

        DemoResetService.ResetResult result = service.resetDemoData("demo-tenant", "tester");

        assertThat(callLog).containsExactly(
                "knowledge:demo-tenant", "workflows:demo-tenant", "customers:demo-tenant");
        assertThat(result.purgedRowsTotal()).isEqualTo(10);
        assertThat(result.purgedRowsByArea())
                .containsEntry("knowledge", 3)
                .containsEntry("workflows", 5)
                .containsEntry("customers", 2);
    }

    @Test
    void seedsTheTenantAfterPurging() {
        DemoResetService service = newService(List.of(new RecordingPurge(10, "knowledge", 1)));

        DemoResetService.ResetResult result = service.resetDemoData("demo-tenant", "tester");

        assertThat(result.seed().tenantId()).isEqualTo("demo-tenant");
        assertThat(callLog).isNotEmpty();
    }

    @Test
    void purgesTheConfiguredTenantWhenNoneIsSupplied() {
        DemoResetService service = newService(List.of(new RecordingPurge(10, "knowledge", 0)));

        service.resetDemoData("  ", "tester");

        assertThat(callLog).containsExactly("knowledge:demo-tenant");
    }

    @Test
    void neverPurgesATenantOtherThanTheRequestedOne() {
        DemoResetService service = newService(List.of(
                new RecordingPurge(10, "knowledge", 0),
                new RecordingPurge(20, "conversations", 0)
        ));

        service.resetDemoData("other-tenant", "tester");

        assertThat(callLog).allMatch(entry -> entry.endsWith(":other-tenant"));
        verifySeededTenant(service);
    }

    private void verifySeededTenant(DemoResetService service) {
        org.mockito.Mockito.verify(seedingService).seedDemoData(eq("other-tenant"), eq("tester"));
    }
}
