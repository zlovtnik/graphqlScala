package com.rcs.ssf.security.mfa;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DEPRECATED: Backward-compatibility shim for the refactored
 * WebAuthnRegistrationOptions.
 *
 * This class delegates to
 * {@link com.rcs.ssf.security.mfa.registration.WebAuthnRegistrationOptions}
 * to maintain API compatibility during the transition period.
 *
 * MIGRATION GUIDE:
 * ================
 * Replace imports from this deprecated class with:
 * {@code import com.rcs.ssf.security.mfa.registration.WebAuthnRegistrationOptions;}
 *
 * This shim will be removed in the next major release.
 * Update your code to use the new import at your earliest convenience.
 *
 * @deprecated Use
 *             {@link com.rcs.ssf.security.mfa.registration.WebAuthnRegistrationOptions}
 *             instead
 */
@Deprecated(since = "0.0.2", forRemoval = true)
public class WebAuthnRegistrationOptions {
    private final com.rcs.ssf.security.mfa.registration.WebAuthnRegistrationOptions delegate;

    /**
     * Creates a new instance with all fields.
     *
     * @param challenge   Base64-encoded challenge
     * @param rp          Relying Party identifier
     * @param userId      User-unique ID within the RP
     * @param username    User's account identifier
     * @param displayName Human-readable user name
     * @param timeout     Max time (ms) to wait for user interaction
     * @param attestation Attestation preference (none, indirect, direct)
     */
    @JsonCreator
    public WebAuthnRegistrationOptions(
            @JsonProperty("challenge") String challenge,
            @JsonProperty("rp") String rp,
            @JsonProperty("userId") String userId,
            @JsonProperty("username") String username,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("timeout") long timeout,
            @JsonProperty("attestation") String attestation) {
        this.delegate = new com.rcs.ssf.security.mfa.registration.WebAuthnRegistrationOptions(
                challenge, rp, userId, username, displayName, timeout, attestation);
    }

    /**
     * Creates a new instance with default values.
     */
    public WebAuthnRegistrationOptions() {
        this.delegate = new com.rcs.ssf.security.mfa.registration.WebAuthnRegistrationOptions();
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public String getChallenge() {
        return delegate.getChallenge();
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public void setChallenge(String challenge) {
        delegate.setChallenge(challenge);
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public String getRp() {
        return delegate.getRp();
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public void setRp(String rp) {
        delegate.setRp(rp);
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public String getUserId() {
        return delegate.getUserId();
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public void setUserId(String userId) {
        delegate.setUserId(userId);
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public String getUsername() {
        return delegate.getUsername();
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public void setUsername(String username) {
        delegate.setUsername(username);
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public void setDisplayName(String displayName) {
        delegate.setDisplayName(displayName);
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public long getTimeout() {
        return delegate.getTimeout();
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public void setTimeout(long timeout) {
        delegate.setTimeout(timeout);
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public String getAttestation() {
        return delegate.getAttestation();
    }

    @Deprecated(since = "0.0.2", forRemoval = true)
    public void setAttestation(String attestation) {
        delegate.setAttestation(attestation);
    }
}
