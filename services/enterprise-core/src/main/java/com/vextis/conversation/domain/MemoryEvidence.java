package com.vextis.conversation.domain;

public record MemoryEvidence(
        String provider,
        boolean available,
        int contextCount,
        boolean preferenceStored
) {
}
