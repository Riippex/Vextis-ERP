package com.vextis.livesession.api.internal;

import com.vextis.livesession.application.LiveSessionValidation;
import com.vextis.livesession.application.ValidateLiveSessionUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LiveSessionValidationController.class)
@Import(LiveSessionToolAuthorizer.class)
class LiveSessionValidationControllerTests {

    private static final UUID SESSION_ID = UUID.fromString("2a6e5e2b-1c8a-4a9e-9b0a-6a2c1d10ab12");
    private static final UUID CONVERSATION_ID = UUID.fromString("6b1a6e4a-2f0a-4e3b-8f0a-9b8b6a2c1d10");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ValidateLiveSessionUseCase validateLiveSession;

    @Test
    void validatesAGenuineTokenWithTheCorrectServiceCredential() throws Exception {
        when(validateLiveSession.validate(eq(SESSION_ID), eq("presented-token-value"))).thenReturn(
                new LiveSessionValidation(true, "demo-tenant", CONVERSATION_ID, Instant.parse("2026-08-25T12:05:00Z")));

        mockMvc.perform(request("Bearer test-service-token", "presented-token-value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.tenantId").value("demo-tenant"))
                .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID.toString()));

        verify(validateLiveSession).validate(SESSION_ID, "presented-token-value");
    }

    @Test
    void rejectsAnInvalidServiceCredentialBeforeCallingTheUseCase() throws Exception {
        mockMvc.perform(request("Bearer wrong-token", "presented-token-value"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(validateLiveSession);
    }

    private static MockHttpServletRequestBuilder request(String authorization, String sessionToken) {
        return post("/internal/agent-tools/v1/live-sessions/{id}/validate", SESSION_ID)
                .header("Authorization", authorization)
                .header("X-Agent-Id", "coordinator-agent")
                .header("X-Correlation-Id", "corr-001")
                .header("X-Live-Session-Token", sessionToken);
    }
}
