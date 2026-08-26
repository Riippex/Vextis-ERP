package com.vextis.conversation.api.graphql;

import com.vextis.conversation.application.AskVextisCommand;
import com.vextis.conversation.application.AskVextisResult;
import com.vextis.conversation.application.AskVextisUseCase;
import com.vextis.conversation.application.FindConversationUseCase;
import com.vextis.conversation.domain.AgentActivityEvidence;
import com.vextis.conversation.domain.ChatMessage;
import com.vextis.conversation.domain.Conversation;
import com.vextis.conversation.domain.MessageKind;
import com.vextis.conversation.domain.MessageSender;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GraphQlTest(AskVextisGraphQlController.class)
@TestPropertySource(properties = "vextis.exposure=PUBLIC")
class AskVextisGraphQlControllerTests {

    private static final UUID CONVERSATION_ID = UUID.fromString("6b1a6e4a-2f0a-4e3b-8f0a-9b8b6a2c1d10");
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private AskVextisUseCase askVextis;

    @MockitoBean
    private FindConversationUseCase findConversation;

    @MockitoBean
    private CurrentActorProvider currentActor;

    @Test
    @WithMockUser(username = "firebase-user-123")
    void postsAMessageAsTheAuthenticatedActor() {
        when(currentActor.currentActorId()).thenReturn("firebase-user-123");
        AgentActivityEvidence evidence = evidence();
        when(askVextis.postMessage(any(AskVextisCommand.class))).thenReturn(
                new AskVextisResult(
                        CONVERSATION_ID, UUID.randomUUID(), "Here is the order status.", NOW, List.of(evidence)));

        graphQlTester.document("""
                        mutation AskVextis($input: AskVextisMessageInput!) {
                          askVextis(input: $input) {
                            conversationId
                            reply
                            agentActivities { agentId displayName tools }
                          }
                        }
                        """)
                .variable("input", Map.of("message", "What is the status of PO-2026-001?"))
                .execute()
                .path("askVextis.conversationId")
                .entity(String.class)
                .isEqualTo(CONVERSATION_ID.toString())
                .path("askVextis.reply")
                .entity(String.class)
                .isEqualTo("Here is the order status.")
                .path("askVextis.agentActivities[0].agentId")
                .entity(String.class)
                .isEqualTo("vextis_inventory_agent")
                .path("askVextis.agentActivities[0].tools[0]")
                .entity(String.class)
                .isEqualTo("get_stock");

        ArgumentCaptor<AskVextisCommand> command = ArgumentCaptor.forClass(AskVextisCommand.class);
        verify(askVextis).postMessage(command.capture());
        assertThat(command.getValue().actorId()).isEqualTo("firebase-user-123");
        assertThat(command.getValue().tenantId()).isEqualTo("demo-tenant");
        assertThat(command.getValue().conversationId()).isNull();
    }

    @Test
    @WithMockUser(username = "firebase-user-123")
    void returnsPersistedConversationHistory() {
        when(findConversation.findById(eq("demo-tenant"), eq(CONVERSATION_ID))).thenReturn(Optional.of(
                new Conversation(CONVERSATION_ID, "demo-tenant", List.of(
                        new ChatMessage(
                                UUID.randomUUID(), MessageSender.USER, "Hello", MessageKind.TEXT, NOW, List.of()),
                        new ChatMessage(
                                UUID.randomUUID(), MessageSender.ASSISTANT, "Hi there", MessageKind.TEXT, NOW,
                                List.of(evidence()))))));

        graphQlTester.document("""
                        query AskVextisConversation($id: ID!) {
                          askVextisConversation(id: $id) {
                            id
                            messages { sender content agentActivities { agentId tools } }
                          }
                        }
                        """)
                .variable("id", CONVERSATION_ID.toString())
                .execute()
                .path("askVextisConversation.messages[0].sender")
                .entity(String.class)
                .isEqualTo("USER")
                .path("askVextisConversation.messages[1].content")
                .entity(String.class)
                .isEqualTo("Hi there")
                .path("askVextisConversation.messages[1].agentActivities[0].agentId")
                .entity(String.class)
                .isEqualTo("vextis_inventory_agent");
    }

    @Test
    @WithMockUser(username = "firebase-user-123")
    void returnsNullForAnUnknownConversation() {
        when(findConversation.findById(eq("demo-tenant"), eq(CONVERSATION_ID))).thenReturn(Optional.empty());

        graphQlTester.document("""
                        query AskVextisConversation($id: ID!) {
                          askVextisConversation(id: $id) { id }
                        }
                        """)
                .variable("id", CONVERSATION_ID.toString())
                .execute()
                .path("askVextisConversation")
                .valueIsNull();
    }

    private static AgentActivityEvidence evidence() {
        return new AgentActivityEvidence(
                "vextis_inventory_agent", "1.0.0", "Inventory Agent", "gemini-3.5-flash", "1.0.0",
                List.of("get_stock"));
    }
}
