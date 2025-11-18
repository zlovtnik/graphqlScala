package com.rcs.ssf.security.mfa.registration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rcs.ssf.security.mfa.validation.ValidBase64;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * WebAuthn registration options sent to the client.
 * Contains the challenge, relying party information, and user identity.
 *
 * Per WebAuthn spec (https://www.w3.org/TR/webauthn-2/#dictdef-publickeycredentialcreationoptions):
 * - challenge: Random bytes (base64-encoded) for cryptographic proof
 * - rp: Relying Party identifier (typically the origin domain)
 * - userId: User-unique ID within the RP (not necessarily email)
 * - username: User's account identifier (display name or username)
 * - displayName: Human-readable user name
 * - timeout: Max time (ms) to wait for user interaction; typically 30-120 seconds
 * - attestation: Preference for attestation conveyance (none, indirect, direct)
 */
public class WebAuthnRegistrationOptions {
    @NotBlank(message = "Challenge is required")
    @ValidBase64(message = "Challenge must be valid Base64 encoding")
    private String challenge; // Base64-encoded challenge
    
    @NotBlank(message = "Relying Party is required")
    private String rp;         // Relying Party
    
    @NotBlank(message = "User ID is required")
    private String userId;     // User ID
    
    @NotBlank(message = "Username is required")
    private String username;   // Username
    
    @NotBlank(message = "Display name is required")
    private String displayName; // User display name
    
    @Positive(message = "Timeout must be positive")
    @Max(value = 300000, message = "Timeout must not exceed 5 minutes")
    private long timeout;      // Timeout in milliseconds (>0, typically 30_000-120_000 ms)
    
    @NotBlank(message = "Attestation is required")
    @Pattern(regexp = "^(none|indirect|direct)$", message = "Attestation must be one of: none, indirect, direct")
    private String attestation; // Attestation preference (none, indirect, direct)

    @JsonCreator
    public WebAuthnRegistrationOptions(
            @JsonProperty("challenge") String challenge,
            @JsonProperty("rp") String rp,
            @JsonProperty("userId") String userId,
            @JsonProperty("username") String username,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("timeout") long timeout,
            @JsonProperty("attestation") String attestation) {
        this.challenge = challenge;
        this.rp = rp;
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.timeout = timeout;
        this.attestation = attestation;
    }

    public WebAuthnRegistrationOptions() {}

    public String getChallenge() { return challenge; }
    public void setChallenge(String challenge) { this.challenge = challenge; }

    public String getRp() { return rp; }
    public void setRp(String rp) { this.rp = rp; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public long getTimeout() { return timeout; }
    public void setTimeout(long timeout) { this.timeout = timeout; }

    public String getAttestation() { return attestation; }
    public void setAttestation(String attestation) { this.attestation = attestation; }
}
