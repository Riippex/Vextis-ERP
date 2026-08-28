package com.vextis.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Public so a slice test can import it alongside an authorizer and exercise the
 * real credential comparison instead of a stub.
 */
@Component
public class ConfiguredServiceCallerIdentities implements ServiceCallerIdentities {

    private final Map<String, byte[]> tokensByIdentity;

    public ConfiguredServiceCallerIdentities(
            @Value("${vextis.agent-tools.service-token:}") String agentToolsToken,
            @Value("${vextis.agent-tools.coordinator-agent-id:coordinator-agent}") String agentToolsIdentity,
            @Value("${vextis.agent-tools.live-gateway-token:}") String liveGatewayToken,
            @Value("${vextis.agent-tools.live-gateway-agent-id:live-gateway-agent}") String liveGatewayIdentity
    ) {
        Map<String, byte[]> configured = new LinkedHashMap<>();
        if (!agentToolsToken.isBlank()) {
            configured.put(agentToolsIdentity, agentToolsToken.getBytes(StandardCharsets.UTF_8));
        }
        if (!liveGatewayToken.isBlank()) {
            if (!agentToolsToken.isBlank() && agentToolsToken.equals(liveGatewayToken)) {
                // Sharing the value would silently collapse the two identities
                // back into one and hand the public gateway the private
                // runtime's authority. Refuse to start rather than pretend.
                throw new IllegalStateException(
                        "vextis.agent-tools.live-gateway-token must differ from "
                                + "vextis.agent-tools.service-token; sharing it defeats the separation");
            }
            configured.put(liveGatewayIdentity, liveGatewayToken.getBytes(StandardCharsets.UTF_8));
        }
        this.tokensByIdentity = Map.copyOf(configured);
    }

    @Override
    public Optional<String> resolve(String presentedToken) {
        if (presentedToken == null || presentedToken.isEmpty()) {
            return Optional.empty();
        }
        byte[] presented = presentedToken.getBytes(StandardCharsets.UTF_8);
        for (Map.Entry<String, byte[]> candidate : tokensByIdentity.entrySet()) {
            if (MessageDigest.isEqual(presented, candidate.getValue())) {
                return Optional.of(candidate.getKey());
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isConfigured() {
        return !tokensByIdentity.isEmpty();
    }
}
