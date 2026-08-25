package com.vextis.conversation.api.graphql;

import com.vextis.conversation.application.AskVextisCommand;
import com.vextis.conversation.application.AskVextisResult;
import com.vextis.conversation.application.AskVextisUseCase;
import com.vextis.conversation.application.FindConversationUseCase;
import com.vextis.conversation.domain.ChatMessage;
import com.vextis.conversation.domain.Conversation;
import com.vextis.shared.security.CurrentActorProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
class AskVextisGraphQlController {

    private final AskVextisUseCase askVextis;
    private final FindConversationUseCase findConversation;
    private final CurrentActorProvider currentActor;
    private final String demoTenantId;

    AskVextisGraphQlController(
            AskVextisUseCase askVextis,
            FindConversationUseCase findConversation,
            CurrentActorProvider currentActor,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String demoTenantId
    ) {
        this.askVextis = askVextis;
        this.findConversation = findConversation;
        this.currentActor = currentActor;
        this.demoTenantId = demoTenantId;
    }

    @MutationMapping
    AskVextisMessageResultView askVextis(@Argument @Valid AskVextisMessageInput input) {
        AskVextisResult result = askVextis.postMessage(new AskVextisCommand(
                demoTenantId,
                currentActor.currentActorId(),
                input.conversationId() == null ? null : UUID.fromString(input.conversationId()),
                input.message()
        ));
        return AskVextisMessageResultView.from(result);
    }

    @QueryMapping
    AskVextisConversationView askVextisConversation(@Argument String id) {
        return findConversation.findById(demoTenantId, UUID.fromString(id))
                .map(AskVextisConversationView::from)
                .orElse(null);
    }

    record AskVextisMessageInput(
            String conversationId,
            @NotBlank @Size(max = 4000) String message
    ) {
    }

    record AskVextisMessageResultView(UUID conversationId, UUID messageId, String reply, String createdAt) {
        static AskVextisMessageResultView from(AskVextisResult result) {
            return new AskVextisMessageResultView(
                    result.conversationId(), result.messageId(), result.reply(), result.createdAt().toString());
        }
    }

    record AskVextisConversationView(UUID id, List<AskVextisMessageView> messages) {
        static AskVextisConversationView from(Conversation conversation) {
            return new AskVextisConversationView(
                    conversation.id(),
                    conversation.messages().stream().map(AskVextisMessageView::from).toList());
        }
    }

    record AskVextisMessageView(UUID id, String sender, String content, String kind, String createdAt) {
        static AskVextisMessageView from(ChatMessage message) {
            return new AskVextisMessageView(
                    message.id(), message.sender().name(), message.content(), message.kind().name(),
                    message.occurredAt().toString());
        }
    }
}
