package com.rcs.ssf.entity;

/**
 * RoleType enum representing the different role types in the system.
 * Implements a hierarchical role structure: SUPER_ADMIN (3) > ADMIN/MFA_ADMIN (2) > USER (1)
 */
public enum RoleType {
    ROLE_USER("Standard user with basic access"),
    ROLE_ADMIN("Administrator with management privileges"),
    ROLE_SUPER_ADMIN("Super administrator with full system access"),
    ROLE_MFA_ADMIN("MFA administrator for credential management");

    private final String description;

    RoleType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get role hierarchy level for comparison.
     * Higher values indicate higher privilege levels.
     */
    public int getHierarchyLevel() {
        return switch (this) {
            case ROLE_SUPER_ADMIN -> 3;
            case ROLE_ADMIN, ROLE_MFA_ADMIN -> 2;
            case ROLE_USER -> 1;
        };
    }

    /**
     * Check if this role has higher privilege than another role
     */
    public boolean isHigherThan(RoleType other) {
        return this.getHierarchyLevel() > other.getHierarchyLevel();
    }

    /**
     * Check if this role has privilege equal to or higher than another role
     */
    public boolean isHigherOrEqualTo(RoleType other) {
        return this.getHierarchyLevel() >= other.getHierarchyLevel();
    }
}
