package com.rcs.ssf.security.mfa.authentication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * Authenticator assertion response from the client during WebAuthn authentication.
 * Contains the cryptographic proof (signature) that the authenticator possesses the private key.
 *
 * Per WebAuthn spec (https://www.w3.org/TR/webauthn-2/#dictionary-assertion-response):
 * - clientDataJSON: Serialized JSON containing the challenge, origin, and type
 * - authenticatorData: Binary data from the authenticator containing challenge verification
 * - signature: Cryptographic signature over clientDataJSON and authenticatorData
 * - userHandle: Optional user handle from the authenticator
 */
public class AuthenticatorAssertionResponse {
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
