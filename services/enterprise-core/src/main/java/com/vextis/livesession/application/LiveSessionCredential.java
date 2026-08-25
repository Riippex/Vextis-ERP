package com.vextis.livesession.application;

import java.time.Instant;
import java.util.UUID;

/** The one and only time the plaintext session token is ever exposed. */
public record LiveSessionCredential(UUID id, String websocketUrl, String sessionToken, Instant expiresAt) {
}
