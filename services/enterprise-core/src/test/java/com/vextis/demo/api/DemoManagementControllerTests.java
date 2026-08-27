package com.vextis.demo.api;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoManagementController.class)
@TestPropertySource(properties = {
        "vextis.agent-tools.service-token=test-demo-token",
        "vextis.demo.tenant-id=demo-tenant"
})
class DemoManagementControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoSeedingService demoSeedingService;

    @Test
    void seed_withValidToken_returns200AndCounts() throws Exception {
        when(demoSeedingService.seedDemoData(eq("demo-tenant"), any())).thenReturn(
                new DemoSeedingService.SeedResult(
                        "demo-tenant",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        mockMvc.perform(post("/internal/demo/seed")
                        .header("Authorization", "Bearer test-demo-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"demo-tenant\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEEDED"))
                .andExpect(jsonPath("$.tenantId").value("demo-tenant"));
    }

    @Test
    void seed_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(post("/internal/demo/seed")
                        .header("Authorization", "Bearer wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"demo-tenant\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seed_missingToken_returns401() throws Exception {
        mockMvc.perform(post("/internal/demo/seed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"demo-tenant\"}"))
                .andExpect(status().isUnauthorized());
    }
}
