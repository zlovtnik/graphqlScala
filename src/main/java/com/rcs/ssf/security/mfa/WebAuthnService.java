package com.rcs.ssf.security.mfa;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * WebAuthn (FIDO2) service for hardware security key support.
 *
 * Responsibilities:
 * - Generate challenge for registration
 * - Register WebAuthn credentials
 * - Generate challenge for authentication
 * - Verify WebAuthn assertions
 *
 * Implementation uses WebAuthn specification (W3C standard).
 */
public interface WebAuthnService {

    /**
     * Start WebAuthn registration for a user.
     *
     * Generates a registration challenge that the user's device must respond to.
    *
    * @param userId nullable user identifier; null indicates a username-less flow
     * @param username username for Relying Party
     * @return registration options (challenge, timeout, supported algorithms, etc.)
     */
    WebAuthnRegistrationOptions startRegistration(@Nullable String userId, String username);

    /**
     * Complete WebAuthn registration.
     *
     * Verifies the response from the user's device and stores the public key.
     *
    * @param userId user identifier (nullable for usernameless initiation)
     * @param registrationResponse response from device
     * @param credentialNickname friendly name for the credential (e.g., "YubiKey 5C")
     * @return credential ID for reference
     * @throws MfaVerificationException if registration fails
     */
    String completeRegistration(@Nullable String userId, WebAuthnRegistrationResponse registrationResponse, String credentialNickname);

    /**
     * Start WebAuthn authentication for a user.
     *
     * Generates an authentication challenge that the user's device must respond to.
    *
    * @param userId nullable user identifier; null indicates username-less flow
     * @return authentication options (challenge, timeout, allowed credentials, etc.)
     */
    WebAuthnAuthenticationOptions startAuthentication(@Nullable String userId);

    /**
     * Complete WebAuthn authentication.
     *
     * Verifies the response from the user's device.
     *
    * @param userId user identifier (nullable for usernameless flows)
     * @param authenticationResponse response from device
     * @return true if authentication succeeds, false otherwise
     * @throws MfaVerificationException if verification fails
     */
    boolean completeAuthentication(@Nullable String userId, WebAuthnAuthenticationResponse authenticationResponse);

    /**
     * List all registered WebAuthn credentials for a user.
     *
    * @param userId user identifier (nullable; null lists credentials for current security context)
     * @return list of credentials with metadata (nickname, created date, last used)
     */
    List<WebAuthnCredential> listCredentials(@Nullable String userId);

    /**
     * Delete a WebAuthn credential.
     *
    * @param userId user identifier (nullable for administrative contexts)
     * @param credentialId credential identifier
     */
    void deleteCredential(@Nullable String userId, String credentialId);

    /**
     * Disable WebAuthn MFA for a user (deletes all credentials).
     *
     * @param userId user identifier (nullable for global disable scenarios)
     */
    void disableWebAuthnMfa(@Nullable String userId);
}
