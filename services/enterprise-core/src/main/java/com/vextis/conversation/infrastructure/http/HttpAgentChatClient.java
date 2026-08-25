package com.vextis.conversation.infrastructure.http;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.vextis.conversation.application.port.AgentChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;

@Component
class HttpAgentChatClient implements AgentChatClient {

    private final RestClient restClient;
    private final String chatUrl;
    private final String callbackToken;
    private final Supplier<IdTokenProvider> idTokenProvider;

    HttpAgentChatClient(
            @Value("${vextis.agent-runtime.chat-url:}") String chatUrl,
            @Value("${vextis.agent-runtime.callback-token:}") String callbackToken
    ) {
        // Built directly rather than injected: RestClient is stateless/thread-safe
        // and nothing else in this service shares a RestClient.Builder bean, so
        // there is no need to depend on Spring Boot's RestClient autoconfiguration.
        this.restClient = RestClient.create();
        this.chatUrl = chatUrl;
        this.callbackToken = callbackToken;
        this.idTokenProvider = memoizedIdTokenProvider();
    }

    @Override
    public String complete(String tenantId, UUID conversationId, String message) {
        if (chatUrl.isBlank() || callbackToken.isBlank()) {
            throw new IllegalStateException("Ask Vextis is not configured to reach Agent Runtime");
        }

        ChatCompleteResponse response = restClient.post()
                .uri(chatUrl)
                .header("Authorization", "Bearer " + callbackToken)
                .header("X-Serverless-Authorization", "Bearer " + fetchIdentityToken())
                .header("X-Tenant-Id", tenantId)
                .header("X-Correlation-Id", conversationId.toString())
                .body(new ChatCompleteRequest(tenantId, conversationId.toString(), message))
                .retrieve()
                .body(ChatCompleteResponse.class);
        if (response == null || response.reply() == null || response.reply().isBlank()) {
            throw new IllegalStateException("Agent Runtime returned an empty chat reply");
        }
        return response.reply();
    }

    private String fetchIdentityToken() {
        try {
            IdTokenCredentials credentials = IdTokenCredentials.newBuilder()
                    .setIdTokenProvider(idTokenProvider.get())
                    .setTargetAudience(chatUrl)
                    .build();
            credentials.refreshIfExpired();
            return credentials.getIdToken().getTokenValue();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not obtain an identity token for Agent Runtime", exception);
        }
    }

    private static Supplier<IdTokenProvider> memoizedIdTokenProvider() {
        return new Supplier<>() {
            private volatile IdTokenProvider provider;

            @Override
            public IdTokenProvider get() {
                IdTokenProvider current = provider;
                if (current == null) {
                    synchronized (this) {
                        current = provider;
                        if (current == null) {
                            current = createIdTokenProvider();
                            provider = current;
                        }
                    }
                }
                return current;
            }
        };
    }

    private static IdTokenProvider createIdTokenProvider() {
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            if (!(credentials instanceof IdTokenProvider provider)) {
                throw new IllegalStateException("Application default credentials cannot mint identity tokens");
            }
            return provider;
        } catch (IOException exception) {
            throw new IllegalStateException("Google application credentials are unavailable", exception);
        }
    }

    record ChatCompleteRequest(String tenantId, String conversationId, String message) {
    }

    record ChatCompleteResponse(String reply) {
    }
}
