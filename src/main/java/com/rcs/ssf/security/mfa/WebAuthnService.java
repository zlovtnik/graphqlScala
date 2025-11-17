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
    * @param userId user identifier (non-null; WebAuthn registration requires an identified user)
     * @param username username for Relying Party
     * @return registration options (challenge, timeout, supported algorithms, etc.)
    * @throws MfaVerificationException if preconditions fail or options cannot be generated
    */
    WebAuthnRegistrationOptions startRegistration(String userId, String username) throws MfaVerificationException;

    /**
     * Complete WebAuthn registration.
     *
     * Verifies the response from the user's device and stores the public key.
     *
    * @param userId user identifier (non-null; not a username-less initiation)
     * @param registrationResponse response from device
     * @param credentialNickname friendly name for the credential (e.g., "YubiKey 5C")
     * @return credential ID for reference
     * @throws MfaVerificationException if registration fails
     */
    String completeRegistration(String userId, WebAuthnRegistrationResponse registrationResponse, String credentialNickname) throws MfaVerificationException;

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
    * @param userId user identifier (nullable for username-less flows)
     * @param authenticationResponse response from device
     * @throws MfaVerificationException if verification fails
     */
    void completeAuthentication(@Nullable String userId, WebAuthnAuthenticationResponse authenticationResponse) throws MfaVerificationException;

    /**
     * List all registered WebAuthn credentials for a user.
     *
     * When {@code userId} is null the service resolves the current user from
     * {@code SecurityContextHolder.getContext().getAuthentication().getPrincipal()}.
     * This uses a thread-local security context derived from the current request's
     * JWT/session. If no authenticated principal is present, implementations SHOULD
     * either throw an {@link org.springframework.security.access.AccessDeniedException}
     * or return an empty list, according to policy.
     *
     * @param userId user identifier (nullable; null lists credentials for current security context)
     * @return list of credentials with metadata (nickname, created date, last used)
     */
    List<WebAuthnCredential> listCredentials(@Nullable String userId);

    /**
        * Delete a WebAuthn credential.
        *
        * Authorization and behavior:
        * - When {@code userId} is non-null: the caller MUST be the owner of the
        *   credential identified by {@code credentialId}, or explicitly authorized to
        *   manage that user's credentials. Implementations MUST validate that
        *   {@code credentialId} belongs to the given {@code userId} before deletion.
        *   If the credential does not belong to {@code userId}, an
        *   {@link IllegalArgumentException} SHOULD be thrown. If the caller is not
        *   authorized, an {@link org.springframework.security.access.AccessDeniedException}
        *   MUST be thrown.
        * - When {@code userId} is null: this is an administrative operation and MUST
        *   only be allowed to callers with an administrator role (for example,
        *   {@code ROLE_ADMIN}). Implementations MUST perform an explicit admin-role
        *   authorization check and MUST emit an audit log entry that includes the
        *   caller identity, target user (if resolved), {@code credentialId}, timestamp,
        *   and reason/context for deletion. Unauthorized callers MUST receive an
        *   {@link org.springframework.security.access.AccessDeniedException}.
        *
        * Recommended exceptions:
        * - {@link org.springframework.security.access.AccessDeniedException} if authorization fails
        * - {@link java.util.NoSuchElementException} if the credential does not exist
        * - {@link IllegalArgumentException} if {@code credentialId} does not belong to {@code userId}
        *
        * Note: For clearer intent, consider splitting this API into two methods in
        * future revisions:
        * - {@code deleteOwnCredential(String credentialId)} — validates ownership in the
        *   current security context.
        * - {@code deleteAdminCredential(String userId, String credentialId)} — enforces
        *   admin authorization and performs audit logging.
        *
        * @param userId user identifier; non-null requires ownership/explicit delegation;
        *               null denotes an admin-only operation
        * @param credentialId credential identifier
        */
    void deleteCredential(@Nullable String userId, String credentialId);

    /**
     * Disable WebAuthn MFA for a user (deletes all credentials).
     * Requires a non-null userId to avoid accidental global disable.
     *
     * @param userId user identifier (required, non-null)
     */
    void disableWebAuthnMfa(String userId);

    /**
     * Administrative: disable WebAuthn for all users.
     * Implementations MUST enforce super-admin authorization, emit detailed audit logs
     * (who/when/why), and require explicit confirmation to proceed.
     *
     * @param adminId administrator performing the action
     * @param reason human-readable reason for disabling
     * @param confirm explicit confirmation flag; MUST be {@code true} to proceed
     */
    void adminDisableWebAuthnForAllUsers(String adminId, String reason, boolean confirm);
}
