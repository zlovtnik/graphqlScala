package com.rcs.ssf.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Stores user preferences including theme, language, and notification settings.
 * Cached in Redis via CacheConfig for fast reads.
 */
@Table("user_preferences")
@Data
@NoArgsConstructor
public class UserPreferences {
    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("theme")
    private String theme = "light"; // light, dark, auto

    @Column("language")
    private String language = "en"; // en, es, fr, de, etc.

    @Column("notification_emails")
    private Boolean notificationEmails = true;

    @Column("notification_push")
    private Boolean notificationPush = true;

    @Column("notification_login_alerts")
    private Boolean notificationLoginAlerts = true;

    @Column("notification_security_updates")
    private Boolean notificationSecurityUpdates = true;

    @Column("created_at")
    private Long createdAt;

    @Column("updated_at")
    private Long updatedAt;

    @PersistenceCreator
    public UserPreferences(Long id, Long userId, String theme, String language,
            Boolean notificationEmails, Boolean notificationPush,
            Boolean notificationLoginAlerts, Boolean notificationSecurityUpdates,
            Long createdAt, Long updatedAt) {
        this.id = id;
        this.userId = userId;
        this.theme = theme != null ? theme : "light";
        this.language = language != null ? language : "en";
        this.notificationEmails = notificationEmails != null ? notificationEmails : true;
        this.notificationPush = notificationPush != null ? notificationPush : true;
        this.notificationLoginAlerts = notificationLoginAlerts != null ? notificationLoginAlerts : true;
        this.notificationSecurityUpdates = notificationSecurityUpdates != null ? notificationSecurityUpdates : true;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UserPreferences(Long userId) {
        this.userId = userId;
        this.theme = "light";
        this.language = "en";
        this.notificationEmails = true;
        this.notificationPush = true;
        this.notificationLoginAlerts = true;
        this.notificationSecurityUpdates = true;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
}
