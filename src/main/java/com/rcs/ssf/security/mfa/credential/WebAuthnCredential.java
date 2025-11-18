package com.rcs.ssf.security.mfa.credential;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rcs.ssf.security.mfa.validation.ValidCredentialTimestamps;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * WebAuthn credential metadata stored on the server.
 * Represents a user's registered WebAuthn authenticator device with metadata.
 *
 * Per WebAuthn spec, credentials are stored server-side with:
 * - credentialId: Binary identifier for the credential (base64-encoded here)
 * - publicKey: The public key from the attestation (used for signature verification)
 * - nickname: Human-readable device label (e.g., "YubiKey 5", "iPhone 14 Pro")
 * - createdAt: Registration timestamp (milliseconds since epoch)
 * - lastUsedAt: Last authentication timestamp; 0 indicates never used
 *
 * Timestamp validation via @ValidCredentialTimestamps ensures logical ordering and no future dates.
 */
@ValidCredentialTimestamps
public class WebAuthnCredential {
    @NotBlank(message = "Credential ID is required")
    private String credentialId;
    
    @Size(max = 100)
    private String nickname;
    
    @jakarta.validation.constraints.Positive(message = "Created timestamp must be positive")
    private long createdAt;
    
    @PositiveOrZero(message = "Last used timestamp must be positive or zero")
    private long lastUsedAt;
    
    @NotBlank(message = "Public key is required")
    @Size(max = 800, message = "Public key must be at most 800 characters")
    private String publicKey; // Base64-encoded, max 800 chars (RSA-4096 + headroom)

    @JsonCreator
    public WebAuthnCredential(
            @JsonProperty("credentialId") String credentialId,
            @JsonProperty("nickname") String nickname,
            @JsonProperty("createdAt") long createdAt,
            @JsonProperty("lastUsedAt") long lastUsedAt,
            @JsonProperty("publicKey") String publicKey) {
        this.credentialId = credentialId;
        this.nickname = nickname;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
        this.publicKey = publicKey;
    }

    public WebAuthnCredential() {}

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(long lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
}
