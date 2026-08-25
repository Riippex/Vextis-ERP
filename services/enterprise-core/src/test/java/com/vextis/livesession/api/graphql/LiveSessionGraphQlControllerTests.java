package com.vextis.livesession.api.graphql;

import com.vextis.livesession.application.CloseLiveSessionUseCase;
import com.vextis.livesession.application.CreateLiveSessionCommand;
import com.vextis.livesession.application.CreateLiveSessionUseCase;
import com.vextis.livesession.application.LiveSessionCredential;
import com.vextis.shared.security.CurrentActorProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GraphQlTest(LiveSessionGraphQlController.class)
@TestPropertySource(properties = "vextis.exposure=PUBLIC")
class LiveSessionGraphQlControllerTests {

    private static final UUID SESSION_ID = UUID.fromString("2a6e5e2b-1c8a-4a9e-9b0a-6a2c1d10ab12");
    private static final UUID CONVERSATION_ID = UUID.fromString("6b1a6e4a-2f0a-4e3b-8f0a-9b8b6a2c1d10");

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private CreateLiveSessionUseCase createLiveSession;

    @MockitoBean
    private CloseLiveSessionUseCase closeLiveSession;

    @MockitoBean
    private CurrentActorProvider currentActor;

    @Test
    @WithMockUser(username = "firebase-user-123")
    void createsASessionForTheAuthenticatedActor() {
        when(currentActor.currentActorId()).thenReturn("firebase-user-123");
        when(createLiveSession.create(any(CreateLiveSessionCommand.class))).thenReturn(new LiveSessionCredential(
                SESSION_ID, "wss://agent-runtime.example.com/v1/live/" + SESSION_ID, "opaque-token",
                Instant.parse("2026-08-25T12:05:00Z")));

        graphQlTester.document("""
                        mutation CreateLiveSession($input: CreateLiveSessionInput!) {
                          createLiveSession(input: $input) {
                            id
                            websocketUrl
                            sessionToken
                            expiresAt
                          }
                        }
                        """)
                .variable("input", Map.of("conversationId", CONVERSATION_ID.toString()))
                .execute()
                .path("createLiveSession.id")
                .entity(String.class)
                .isEqualTo(SESSION_ID.toString())
                .path("createLiveSession.sessionToken")
                .entity(String.class)
                .isEqualTo("opaque-token");

        ArgumentCaptor<CreateLiveSessionCommand> command = ArgumentCaptor.forClass(CreateLiveSessionCommand.class);
        verify(createLiveSession).create(command.capture());
        assertThat(command.getValue().actorId()).isEqualTo("firebase-user-123");
        assertThat(command.getValue().tenantId()).isEqualTo("demo-tenant");
        assertThat(command.getValue().conversationId()).isEqualTo(CONVERSATION_ID);
    }

    @Test
    @WithMockUser(username = "firebase-user-123")
    void closesASessionForTheDemoTenant() {
        when(closeLiveSession.close(eq("demo-tenant"), eq(SESSION_ID))).thenReturn(true);

        graphQlTester.document("""
                        mutation CloseLiveSession($id: ID!) {
                          closeLiveSession(id: $id)
                        }
                        """)
                .variable("id", SESSION_ID.toString())
                .execute()
                .path("closeLiveSession")
                .entity(Boolean.class)
                .isEqualTo(true);
    }
}
