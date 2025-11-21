package com.rcs.ssf.util;

/**
 * Constants for account-related operations.
 * Consolidates account reason codes and other account constants.
 */
public final class AccountConstants {
    private AccountConstants() {
        // Utility class - prevent instantiation
    }

    /**
     * Default reason code for account deactivation when not explicitly provided.
     */
    public static final String DEFAULT_REASON_CODE = "USER_INITIATED";

    /**
     * Reason code for user-requested account deactivation.
     */
    public static final String REASON_USER_REQUESTED = "USER_REQUESTED";

    /**
     * Reason code for admin-initiated account deactivation.
     */
    public static final String REASON_ADMIN_ACTION = "ADMIN_ACTION";

    /**
     * Reason code for inactivity-based account deactivation.
     */
    public static final String REASON_INACTIVITY = "INACTIVITY";

    /**
     * Reason code for policy violation-based account deactivation.
     */
    public static final String REASON_POLICY_VIOLATION = "POLICY_VIOLATION";

    /**
     * Reason code for abuse-based account suspension.
     */
    public static final String REASON_ABUSE = "ABUSE";
}
