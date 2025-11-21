package com.rcs.ssf.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
@Data
@NoArgsConstructor
public class User {
    @Id
    private Long id;

    @Column("username")
    @NotBlank(message = "Username is required and cannot be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @Column("password")
    @NotBlank(message = "Password is required and cannot be blank")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters (aligned with AuthRequest)")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column("email")
    @NotBlank(message = "Email is required and cannot be blank")
    @Email(message = "Email should be valid")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    private String email;

    @Column("avatar_key")
    private String avatarKey;

    @Column("account_status")
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column("account_deactivated_at")
    private Long accountDeactivatedAt;

    @PersistenceCreator
    public User(Long id, String username, String password, String email, String avatarKey,
            String accountStatus, Long accountDeactivatedAt) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.avatarKey = avatarKey;
        // Defensively parse account status, falling back to ACTIVE for invalid values
        try {
            this.accountStatus = accountStatus != null ? AccountStatus.valueOf(accountStatus) : AccountStatus.ACTIVE;
        } catch (IllegalArgumentException | NullPointerException e) {
            org.slf4j.LoggerFactory.getLogger(User.class)
                .warn("Invalid account status value: '{}', falling back to ACTIVE", accountStatus, e);
            this.accountStatus = AccountStatus.ACTIVE;
        }
        this.accountDeactivatedAt = accountDeactivatedAt;
    }

    @PersistenceCreator
    public User(Long id, String username, String password, String email) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.accountStatus = AccountStatus.ACTIVE;
    }

    // Custom constructor for convenient creation
    /**
     * Creates a new User with required fields validated.
     *
     * <p><strong>Validation Strategy:</strong> This constructor performs defensive null/blank checks
     * that run immediately, before bean validation occurs. The {@code @NotBlank} field constraints
     * provide additional protection in validation-aware code paths (e.g., after {@code @Valid} filtering).
     * If this constructor is used from such paths, the exceptions thrown here may never surface.
     * For non-validated contexts (e.g., internal service construction), these checks ensure data integrity
     * at creation time.
     *
     * @param username the username (required, non-blank)
     * @param password the password (required, non-blank)
     * @param email the email address (required, non-blank)
     * @throws IllegalArgumentException if username is null or blank
     * @throws IllegalArgumentException if password is null or blank
     * @throws IllegalArgumentException if email is null or blank
     */
    public User(String username, String password, String email) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required and cannot be null or blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required and cannot be null or blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required and cannot be null or blank");
        }
        this.username = username;
        this.password = password;
        this.email = email;
        this.accountStatus = AccountStatus.ACTIVE;
    }
}
