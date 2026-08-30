package com.vextis.crm;

/**
 * Thrown when an idempotency key is replayed with a quote id or payload that
 * does not match the asset originally registered under that key. Reusing a
 * key across different quotes or content must fail loudly instead of
 * silently returning the unrelated original asset.
 */
public class ProposalAssetConflictException extends RuntimeException {

    public ProposalAssetConflictException(String message) {
        super(message);
    }
}
