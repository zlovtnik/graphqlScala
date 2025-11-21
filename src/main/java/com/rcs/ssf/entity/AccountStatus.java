package com.rcs.ssf.entity;

/**
 * Enumeration of account statuses.
 * Replaces String-based status values with type-safe enum.
 */
public enum AccountStatus {
    /**
     * Account is active and can be used normally.
     */
    ACTIVE,

    /**
     * Account has been deactivated by the user or administrator.
     */
    DEACTIVATED,

    /**
     * Account has been suspended due to policy violation or abuse.
     */
    SUSPENDED
}
