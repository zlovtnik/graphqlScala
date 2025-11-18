package com.rcs.ssf.security.mfa.authentication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * Authenticator assertion response from the client during WebAuthn
 * authentication.
 * Contains the cryptographic proof (signature) that the authenticator possesses
 * the private key.
 *
 * Per WebAuthn spec
 * (https://www.w3.org/TR/webauthn-2/#dictionary-assertion-response):
 * - clientDataJSON: Serialized JSON containing the challenge, origin, and type
 * - authenticatorData: Binary data from the authenticator containing challenge
 * verification
 * - signature: Cryptographic signature over clientDataJSON and
 * authenticatorData
 * - userHandle: Optional user handle from the authenticator
 */
public final class AuthenticatorAssertionResponse {
    @NotNull(message = "Client data JSON is required")
    private final byte[] clientDataJSON; // ClientDataJSON in base64 format

    @NotNull(message = "Authenticator data is required")
    private final byte[] authenticatorData; // Authenticator data in base64 format

    @NotNull(message = "Signature is required")
    private final byte[] signature; // Signature in base64 format

    @Nullable
    private final byte[] userHandle; // User handle (optional, may be null)

    /**
     * Constructs an AuthenticatorAssertionResponse with required cryptographic proof.
     *
     * @param clientDataJSON the serialized client data JSON (required, non-null)
     * @param authenticatorData the authenticator data (required, non-null)
     * @param signature the cryptographic signature (required, non-null)
     * @param userHandle the optional user handle from the authenticator (may be null)
     * @throws NullPointerException if clientDataJSON, authenticatorData, or signature is null
     */
    @JsonCreator
    public AuthenticatorAssertionResponse(
            @JsonProperty("clientDataJSON") byte[] clientDataJSON,
            @JsonProperty("authenticatorData") byte[] authenticatorData,
            @JsonProperty("signature") byte[] signature,
            @JsonProperty("userHandle") byte[] userHandle) {
        // Enforce non-null preconditions at construction time
        this.clientDataJSON = Objects.requireNonNull(clientDataJSON, "Client data JSON is required").clone();
        this.authenticatorData = Objects.requireNonNull(authenticatorData, "Authenticator data is required").clone();
        this.signature = Objects.requireNonNull(signature, "Signature is required").clone();
        // userHandle is optional and may remain null
        this.userHandle = userHandle != null ? userHandle.clone() : null;
    }

    public byte[] getClientDataJSON() {
        return clientDataJSON != null ? clientDataJSON.clone() : null;
    }

    public byte[] getAuthenticatorData() {
        return authenticatorData != null ? authenticatorData.clone() : null;
    }

    public byte[] getSignature() {
        return signature != null ? signature.clone() : null;
    }

    public byte[] getUserHandle() {
        return userHandle != null ? userHandle.clone() : null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        AuthenticatorAssertionResponse other = (AuthenticatorAssertionResponse) obj;
        return java.util.Arrays.equals(clientDataJSON, other.clientDataJSON)
                && java.util.Arrays.equals(authenticatorData, other.authenticatorData)
                && java.util.Arrays.equals(signature, other.signature)
                && java.util.Arrays.equals(userHandle, other.userHandle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                java.util.Arrays.hashCode(clientDataJSON),
                java.util.Arrays.hashCode(authenticatorData),
                java.util.Arrays.hashCode(signature),
                java.util.Arrays.hashCode(userHandle)
        );
    }
}
