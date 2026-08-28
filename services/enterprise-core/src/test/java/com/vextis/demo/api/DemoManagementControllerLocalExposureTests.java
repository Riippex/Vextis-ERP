package com.vextis.demo.api;

import com.vextis.demo.DemoResetService;
import com.vextis.demo.DemoSeedingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Local development keeps working without a secret manager, and only there:
 * the credential is waived for LOCAL exposure alone.
 */
@WebMvcTest(DemoManagementController.class)
@TestPropertySource(properties = {
        "vextis.demo.admin-token=",
        "vextis.demo.tenant-id=demo-tenant",
        "vextis.exposure=LOCAL"
})
class DemoManagementControllerLocalExposureTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoSeedingService demoSeedingService;

    @MockitoBean
    private DemoResetService demoResetService;

    @Test
    void seedsWithoutACredentialOnlyWhenExposureIsLocal() throws Exception {
        when(demoSeedingService.seedDemoData(eq("demo-tenant"), any())).thenReturn(
                new DemoSeedingService.SeedResult(
                        "demo-tenant", List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(post("/internal/demo/seed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"demo-tenant\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void stillRefusesAForeignTenantLocally() throws Exception {
        mockMvc.perform(post("/internal/demo/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"acme-production\"}"))
                .andExpect(status().isForbidden());
    }
}
