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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What demo administration does when no administrative credential is set.
 *
 * <p>Separate class rather than a nested one: it needs its own Spring context,
 * and a nested class shares the enclosing one.
 */
@WebMvcTest(DemoManagementController.class)
@TestPropertySource(properties = {
        "vextis.demo.admin-token=",
        "vextis.demo.tenant-id=demo-tenant",
        "vextis.exposure=INTERNAL"
})
class DemoManagementControllerExposureTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoSeedingService demoSeedingService;

    @MockitoBean
    private DemoResetService demoResetService;

    @Test
    void demoAdministrationIsDisabledRatherThanOpenWhenNoCredentialIsConfigured() throws Exception {
        // An unset token used to skip the check entirely, which left seeding and
        // the destructive reset open to anyone who could reach the service.
        mockMvc.perform(post("/internal/demo/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"demo-tenant\"}"))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(demoResetService);
        verifyNoInteractions(demoSeedingService);
    }

    @Test
    void seedingIsDisabledTooRatherThanOpen() throws Exception {
        mockMvc.perform(post("/internal/demo/seed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"demo-tenant\"}"))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(demoSeedingService);
    }
}
