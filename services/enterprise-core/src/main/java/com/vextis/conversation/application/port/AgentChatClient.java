package com.vextis.conversation.application.port;

import java.util.List;
import java.util.UUID;

public interface AgentChatClient {
    /**
     * Sends the user's message to Agent Runtime's coordinator and returns its
     * text reply. Any business mutation the agent decides to make happens on
     * its side through the existing authenticated /internal/agent-tools/**
     * path — this call only carries the conversation turn.
     */
    ChatCompletion complete(String tenantId, UUID conversationId, String message);

    record ChatCompletion(String reply, List<AgentActivity> activities) {
        public ChatCompletion {
            activities = List.copyOf(activities);
        }
    }

    record AgentActivity(String agentId, List<String> tools) {
        public AgentActivity {
            tools = List.copyOf(tools);
        }
    }
}
