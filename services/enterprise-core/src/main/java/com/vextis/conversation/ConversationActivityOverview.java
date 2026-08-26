package com.vextis.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ConversationActivityOverview {

    List<RecentAgentActivity> findRecentAgentActivities(String tenantId, int limit);

    record RecentAgentActivity(
            UUID conversationId,
            UUID messageId,
            String agentId,
            String agentVersion,
            String displayName,
            String modelId,
            String promptVersion,
            List<String> tools,
            Instant occurredAt
    ) {
        public RecentAgentActivity {
            tools = List.copyOf(tools);
        }
    }
}
