package com.vextis.shared;

/**
 * Removes one module's rows for a tenant.
 *
 * <p>Each module implements this over the tables it owns, so a tenant-wide
 * reset never becomes one service reaching across module boundaries with raw
 * SQL. The demo reset endpoint runs every implementation in {@link #order()}
 * and reports what each removed.
 *
 * <p>Deliberately not implemented by the audit module: an audit trail that a
 * demo reset can erase is not an audit trail.
 */
public interface TenantDataPurge {

    /**
     * Relative execution order, lowest first. Modules whose rows are referenced
     * by others declare a lower number so foreign keys are satisfied without a
     * cross-module coordinator knowing the schema.
     */
    int order();

    /** Short area name reported back to the caller, for example {@code knowledge}. */
    String area();

    /** Deletes this module's rows for the tenant and returns how many were removed. */
    int purgeTenant(String tenantId);
}
