package com.rcs.ssf.security.mfa;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;
import java.util.Base64;

/**
 * Validates that a string is valid Base64 encoding (standard or URL-safe).
 * Accepts strings with proper padding or without padding.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidBase64.Validator.class)
@interface ValidBase64 {
    String message() default "Invalid Base64 encoding";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidBase64, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                // Null/blank is handled by @NotBlank; this validator only checks format
                return true;
            }

            try {
                // Try standard Base64 decoder first
                Base64.getDecoder().decode(value);
                return true;
            } catch (IllegalArgumentException e) {
                // Try URL-safe decoder as fallback
                try {
                    Base64.getUrlDecoder().decode(value);
                    return true;
                } catch (IllegalArgumentException ex) {
                    // Neither decoder succeeded; invalid Base64
                    return false;
                }
            }
        }
    }
}

/**
 * Validates that WebAuthn credential timestamps are temporally consistent.
 * Ensures createdAt ≤ lastUsedAt and prevents future-dated credentials.
 * Note: lastUsedAt == 0 is treated as a sentinel value indicating the credential has never been used.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidCredentialTimestamps.Validator.class)
@interface ValidCredentialTimestamps {
    String message() default "Credential timestamps are invalid: createdAt must not exceed lastUsedAt, and neither should be in the future";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidCredentialTimestamps, WebAuthnCredential> {
        private static final long MAX_FUTURE_DRIFT_MS = 5000; // Allow 5s clock skew

        @Override
        public boolean isValid(WebAuthnCredential cred, ConstraintValidatorContext context) {
            if (cred == null) return true;

            long now = System.currentTimeMillis();

            // Validate createdAt is not in the future
            if (cred.getCreatedAt() > now + MAX_FUTURE_DRIFT_MS) {
                return false;
            }

            // Validate lastUsedAt is not in the future (unless 0, indicating never used)
            if (cred.getLastUsedAt() != 0 && cred.getLastUsedAt() > now + MAX_FUTURE_DRIFT_MS) {
                return false;
            }

            // Validate createdAt ≤ lastUsedAt (when lastUsedAt is set)
            if (cred.getLastUsedAt() != 0 && cred.getLastUsedAt() < cred.getCreatedAt()) {
                return false;
            }

            return true;
        }
    }
}


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

/**
 * AuthenticatorAttestationResponse containing client data and attestation object.
 */
class AuthenticatorAttestationResponse {
    @NotNull(message = "Client data JSON is required")
    @Size(min = 1, message = "Client data JSON must not be empty")
    private byte[] clientDataJSON;  // Base64-encoded client data
    
    @NotNull(message = "Attestation object is required")
    @Size(min = 1, message = "Attestation object must not be empty")
    private byte[] attestationObject;  // Base64-encoded attestation object

    @JsonCreator
    public AuthenticatorAttestationResponse(
            @JsonProperty("clientDataJSON") byte[] clientDataJSON,
            @JsonProperty("attestationObject") byte[] attestationObject) {
        this.clientDataJSON = clientDataJSON;
        this.attestationObject = attestationObject;
    }

    public AuthenticatorAttestationResponse() {}

    public byte[] getClientDataJSON() { return clientDataJSON; }
    public void setClientDataJSON(byte[] clientDataJSON) { this.clientDataJSON = clientDataJSON; }

    public byte[] getAttestationObject() { return attestationObject; }
    public void setAttestationObject(byte[] attestationObject) { this.attestationObject = attestationObject; }
}

/**
 * WebAuthn registration response from client.
 * Properly typed and validated DTO.
 */
class WebAuthnRegistrationResponse {
    @NotBlank(message = "ID is required")
    private String id;           // Credential ID
    
    @NotNull(message = "Raw ID is required")
    @Size(min = 1, message = "Raw ID must not be empty")
    private byte[] rawId;        // Raw credential ID (binary)
    
    @NotNull(message = "Authenticator response is required")
    @Valid
    private AuthenticatorAttestationResponse response;  // Attestation response
    
    @NotBlank(message = "Type is required")
    @Pattern(regexp = "^public-key$", message = "Type must be 'public-key'")
    private String type;         // Type ("public-key")

    @JsonCreator
    public WebAuthnRegistrationResponse(
            @JsonProperty("id") String id,
            @JsonProperty("rawId") byte[] rawId,
            @JsonProperty("response") AuthenticatorAttestationResponse response,
            @JsonProperty("type") String type) {
        this.id = id;
        this.rawId = rawId;
        this.response = response;
        this.type = type;
    }

    public WebAuthnRegistrationResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public byte[] getRawId() { return rawId; }
    public void setRawId(byte[] rawId) { this.rawId = rawId; }

    public AuthenticatorAttestationResponse getResponse() { return response; }
    public void setResponse(AuthenticatorAttestationResponse response) { this.response = response; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}

/**
 * WebAuthn authentication options returned to client.
 */
class WebAuthnAuthenticationOptions {
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

/**
 * Authenticator assertion response containing cryptographic proof.
 */
class AuthenticatorAssertionResponse {
    @NotNull(message = "Client data JSON is required")
    private byte[] clientDataJSON;  // ClientDataJSON in base64 format
    
    @NotNull(message = "Authenticator data is required")
    private byte[] authenticatorData;  // Authenticator data in base64 format
    
    @NotNull(message = "Signature is required")
    private byte[] signature;       // Signature in base64 format
    
    @Nullable
    private byte[] userHandle;      // User handle (optional, may be null)

    @JsonCreator
    public AuthenticatorAssertionResponse(
            @JsonProperty("clientDataJSON") byte[] clientDataJSON,
            @JsonProperty("authenticatorData") byte[] authenticatorData,
            @JsonProperty("signature") byte[] signature,
            @JsonProperty("userHandle") byte[] userHandle) {
        this.clientDataJSON = clientDataJSON;
        this.authenticatorData = authenticatorData;
        this.signature = signature;
        this.userHandle = userHandle;
    }

    public AuthenticatorAssertionResponse() {}

    public byte[] getClientDataJSON() { return clientDataJSON; }
    public void setClientDataJSON(byte[] clientDataJSON) { this.clientDataJSON = clientDataJSON; }

    public byte[] getAuthenticatorData() { return authenticatorData; }
    public void setAuthenticatorData(byte[] authenticatorData) { this.authenticatorData = authenticatorData; }

    public byte[] getSignature() { return signature; }
    public void setSignature(byte[] signature) { this.signature = signature; }

    public byte[] getUserHandle() { return userHandle; }
    public void setUserHandle(byte[] userHandle) { this.userHandle = userHandle; }
}

/**
 * WebAuthn authentication response from client.
 */
class WebAuthnAuthenticationResponse {
    @NotBlank(message = "ID is required")
    private String id;              // Credential ID
    
    @NotNull(message = "Raw ID is required")
    @Size(min = 1, message = "Raw ID must not be empty")
    private byte[] rawId;           // Raw credential ID (binary)
    
    @NotNull(message = "Authenticator response is required")
    @Valid
    private AuthenticatorAssertionResponse response;  // Authenticator assertion
    
    @NotBlank(message = "Type is required")
    @Pattern(regexp = "^public-key$", message = "Type must be 'public-key'")
    private String type;            // Type ("public-key")

    @JsonCreator
    public WebAuthnAuthenticationResponse(
            @JsonProperty("id") String id,
            @JsonProperty("rawId") byte[] rawId,
            @JsonProperty("response") AuthenticatorAssertionResponse response,
            @JsonProperty("type") String type) {
        this.id = id;
        this.rawId = rawId;
        this.response = response;
        this.type = type;
    }

    public WebAuthnAuthenticationResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public byte[] getRawId() { return rawId; }
    public void setRawId(byte[] rawId) { this.rawId = rawId; }

    public AuthenticatorAssertionResponse getResponse() { return response; }
    public void setResponse(AuthenticatorAssertionResponse response) { this.response = response; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}

/**
 * WebAuthn credential metadata.
 */
@ValidCredentialTimestamps
class WebAuthnCredential {
    @NotBlank(message = "Credential ID is required")
    private String credentialId;
    
    @Size(max = 100)
    private String nickname;
    
    @Positive(message = "Created timestamp must be positive")
    private long createdAt;
    
    @PositiveOrZero(message = "Last used timestamp must be positive or zero")
    private long lastUsedAt;
    
    @NotBlank(message = "Public key is required")
    private String publicKey; // Base64-encoded

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

