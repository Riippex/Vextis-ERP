package com.vextis.conversation.domain;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(
        UUID id,
        MessageSender sender,
        String content,
        MessageKind kind,
        Instant occurredAt
) {
}
