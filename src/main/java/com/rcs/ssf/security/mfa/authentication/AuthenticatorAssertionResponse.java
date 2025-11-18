package com.rcs.ssf.security.mfa.authentication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

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
public class AuthenticatorAssertionResponse {
    @NotNull(message = "Client data JSON is required")
    private byte[] clientDataJSON; // ClientDataJSON in base64 format

    @NotNull(message = "Authenticator data is required")
    private byte[] authenticatorData; // Authenticator data in base64 format

    @NotNull(message = "Signature is required")
    private byte[] signature; // Signature in base64 format

    @Nullable
    private byte[] userHandle; // User handle (optional, may be null)

    @JsonCreator
    public AuthenticatorAssertionResponse(
            @JsonProperty("clientDataJSON") byte[] clientDataJSON,
            @JsonProperty("authenticatorData") byte[] authenticatorData,
            @JsonProperty("signature") byte[] signature,
            @JsonProperty("userHandle") byte[] userHandle) {
        this.clientDataJSON = clientDataJSON != null ? clientDataJSON.clone() : null;
        this.authenticatorData = authenticatorData != null ? authenticatorData.clone() : null;
        this.signature = signature != null ? signature.clone() : null;
        this.userHandle = userHandle != null ? userHandle.clone() : null;
    }

    public AuthenticatorAssertionResponse() {
    }

    public byte[] getClientDataJSON() {
        return clientDataJSON != null ? clientDataJSON.clone() : null;
    }

    public void setClientDataJSON(byte[] clientDataJSON) {
        this.clientDataJSON = clientDataJSON != null ? clientDataJSON.clone() : null;
    }

    public byte[] getAuthenticatorData() {
        return authenticatorData != null ? authenticatorData.clone() : null;
    }

    public void setAuthenticatorData(byte[] authenticatorData) {
        this.authenticatorData = authenticatorData != null ? authenticatorData.clone() : null;
    }

    public byte[] getSignature() {
        return signature != null ? signature.clone() : null;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature != null ? signature.clone() : null;
    }

    public byte[] getUserHandle() {
        return userHandle != null ? userHandle.clone() : null;
    }

    public void setUserHandle(byte[] userHandle) {
        this.userHandle = userHandle != null ? userHandle.clone() : null;
    }
}
