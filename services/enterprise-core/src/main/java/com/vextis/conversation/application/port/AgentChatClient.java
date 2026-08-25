package com.vextis.conversation.application.port;

import java.util.UUID;

public interface AgentChatClient {
    /**
     * Sends the user's message to Agent Runtime's coordinator and returns its
     * text reply. Any business mutation the agent decides to make happens on
     * its side through the existing authenticated /internal/agent-tools/**
     * path — this call only carries the conversation turn.
     */
    String complete(String tenantId, UUID conversationId, String message);
}
