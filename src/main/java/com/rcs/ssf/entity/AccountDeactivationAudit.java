package com.rcs.ssf.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Audit entity for tracking account deactivations.
 * Immutable record of when and why an account was deactivated.
 */
@Table("account_deactivation_audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeactivationAudit {
    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("timestamp")
    private Long timestamp;

    @Column("reason_code")
    private String reasonCode;

    @Column("free_text_justification")
    private String freeTextJustification;

    /**
     * Constructor for creating a new audit entry.
     * Timestamp is set automatically to the current time.
     *
     * @param userId the ID of the user being deactivated
     * @param reasonCode the discrete reason code (e.g., "USER_REQUESTED", "INACTIVITY", "ABUSE")
     * @param freeTextJustification optional free-text explanation
     */
    public AccountDeactivationAudit(Long userId, String reasonCode, String freeTextJustification) {
        this.userId = userId;
        this.timestamp = System.currentTimeMillis();
        this.reasonCode = reasonCode;
        this.freeTextJustification = freeTextJustification;
    }
}
