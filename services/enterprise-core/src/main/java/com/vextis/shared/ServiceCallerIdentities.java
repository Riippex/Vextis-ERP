package com.vextis.shared;

import java.util.Optional;

/**
 * Resolves an opaque service credential to the service identity that presented
 * it.
 *
 * <p>Enterprise Core used to accept a single shared token, so every caller
 * reaching {@code /internal/agent-tools/**} was the same principal. That made a
 * publicly reachable Live gateway indistinguishable from the private Agent
 * Runtime: one leaked credential would have carried the authority to record
 * plans, reserve stock and issue invoices.
 *
 * <p>Each configured credential now maps to its own service identity, and the
 * agent registry decides what that identity may do through the
 * {@code service_identity} of each active entry. Separating the credential is
 * only worth anything because the identity narrows the tool allowlist with it.
 */
public interface ServiceCallerIdentities {

    /**
     * Service identity for the presented token, or empty when it matches no
     * configured credential.
     */
    Optional<String> resolve(String presentedToken);

    /** False when no service credential is configured at all. */
    boolean isConfigured();
}
