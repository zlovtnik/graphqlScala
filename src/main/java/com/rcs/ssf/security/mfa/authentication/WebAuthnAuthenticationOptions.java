package com.rcs.ssf.security.mfa.authentication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rcs.ssf.security.mfa.validation.ValidBase64;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * WebAuthn authentication options sent to the client.
 * Provides the challenge and authentication parameters for user verification.
 *
 * Per WebAuthn spec (https://www.w3.org/TR/webauthn-2/#dictdef-publickeycredentialrequestoptions):
 * - challenge: Random bytes (base64-encoded) for cryptographic proof
 * - timeout: Max time (ms) to wait for user interaction; typically 30-120 seconds
 * - userVerification: Requirement level for user verification (required, preferred, discouraged)
 */
public class WebAuthnAuthenticationOptions {
    @NotBlank(message = "Challenge is required")
    @ValidBase64(message = "Challenge must be valid Base64 encoding")
    private String challenge;        // Base64-encoded challenge
    
    @Positive(message = "Timeout must be positive")
    @Max(value = 300000, message = "Timeout must not exceed 5 minutes")
    private long timeout;           // Timeout in milliseconds
    
    @NotBlank(message = "User verification is required")
    @Pattern(regexp = "^(required|preferred|discouraged)$", message = "User verification must be one of: required, preferred, discouraged")
    private String userVerification; // Verification requirement

    @JsonCreator
    public WebAuthnAuthenticationOptions(
            @JsonProperty("challenge") String challenge,
            @JsonProperty("timeout") long timeout,
            @JsonProperty("userVerification") String userVerification) {
        this.challenge = challenge;
        this.timeout = timeout;
        this.userVerification = userVerification;
    }

    public WebAuthnAuthenticationOptions() {}

    public String getChallenge() { return challenge; }
    public void setChallenge(String challenge) { this.challenge = challenge; }

    public long getTimeout() { return timeout; }
    public void setTimeout(long timeout) { this.timeout = timeout; }

    public String getUserVerification() { return userVerification; }
    public void setUserVerification(String userVerification) { this.userVerification = userVerification; }
}
