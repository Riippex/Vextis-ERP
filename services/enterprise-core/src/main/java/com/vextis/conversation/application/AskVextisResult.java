package com.vextis.conversation.application;

import java.time.Instant;
import java.util.UUID;

public record AskVextisResult(UUID conversationId, UUID messageId, String reply, Instant createdAt) {
}
