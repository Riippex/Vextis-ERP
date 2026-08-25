package com.vextis.livesession.api.graphql;

import com.vextis.livesession.application.CloseLiveSessionUseCase;
import com.vextis.livesession.application.CreateLiveSessionCommand;
import com.vextis.livesession.application.CreateLiveSessionUseCase;
import com.vextis.livesession.application.LiveSessionCredential;
import com.vextis.shared.security.CurrentActorProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
class LiveSessionGraphQlController {

    private final CreateLiveSessionUseCase createLiveSession;
    private final CloseLiveSessionUseCase closeLiveSession;
    private final CurrentActorProvider currentActor;
    private final String demoTenantId;

    LiveSessionGraphQlController(
            CreateLiveSessionUseCase createLiveSession,
            CloseLiveSessionUseCase closeLiveSession,
            CurrentActorProvider currentActor,
            @Value("${vextis.demo.tenant-id:demo-tenant}") String demoTenantId
    ) {
        this.createLiveSession = createLiveSession;
        this.closeLiveSession = closeLiveSession;
        this.currentActor = currentActor;
        this.demoTenantId = demoTenantId;
    }

    @MutationMapping
    LiveSessionView createLiveSession(@Argument @Valid CreateLiveSessionInput input) {
        LiveSessionCredential credential = createLiveSession.create(new CreateLiveSessionCommand(
                demoTenantId, currentActor.currentActorId(), UUID.fromString(input.conversationId())));
        return LiveSessionView.from(credential);
    }

    @MutationMapping
    boolean closeLiveSession(@Argument String id) {
        return closeLiveSession.close(demoTenantId, UUID.fromString(id));
    }

    record CreateLiveSessionInput(@NotNull String conversationId) {
    }

    record LiveSessionView(UUID id, String websocketUrl, String sessionToken, String expiresAt) {
        static LiveSessionView from(LiveSessionCredential credential) {
            return new LiveSessionView(
                    credential.id(), credential.websocketUrl(), credential.sessionToken(),
                    credential.expiresAt().toString());
        }
    }
}
