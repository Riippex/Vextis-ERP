package com.vextis.conversation.domain;

import java.util.List;

public record AgentActivityEvidence(
        String agentId,
        String agentVersion,
        String displayName,
        String modelId,
        String promptVersion,
        List<String> tools
) {
    public AgentActivityEvidence {
        tools = List.copyOf(tools);
    }
}
