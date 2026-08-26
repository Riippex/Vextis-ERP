package com.vextis.agentregistry;

import java.util.List;
import java.util.Optional;

public interface AgentDirectory {

    List<AgentRegistration> findAll(String tenantId);

    Optional<AgentRegistration> findActive(String tenantId, String agentId);

    record AgentRegistration(
            String agentId,
            String version,
            String displayName,
            String department,
            String purpose,
            String framework,
            String modelId,
            String promptVersion,
            String serviceIdentity,
            String status,
            List<String> capabilities,
            List<String> allowedTools
    ) {
    }
}
