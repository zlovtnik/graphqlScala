package com.rcs.ssf.security.mfa.registration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Authenticator attestation response from the client during WebAuthn
 * registration.
 * Contains the ClientDataJSON and attestation object returned by the
 * authenticator device.
 *
 * These fields are typically received as base64-encoded strings from the
 * browser's
 * navigator.credentials.create() response and contain cryptographic proof of
 * the
 * authenticator's legitimacy and the challenge.
 *
 * Per WebAuthn spec
 * (https://www.w3.org/TR/webauthn-2/#dictionary-attestation-response):
 * - clientDataJSON: Serialized JSON containing the challenge, origin, and type
 * - attestationObject: CBOR-encoded object containing attestation data and auth
 * data
 */
public class AuthenticatorAttestationResponse {
    @NotNull(message = "Client data JSON is required")
    @Size(min = 1, max = 10240, message = "Client data JSON must be between 1 and 10KB")
    private byte[] clientDataJSON; // Decoded client data JSON bytes

    @NotNull(message = "Attestation object is required")
    @Size(min = 1, max = 10240, message = "Attestation object must be between 1 and 10KB")
    private byte[] attestationObject; // Decoded attestation object bytes (CBOR)

    @JsonCreator
    public AuthenticatorAttestationResponse(
            @JsonProperty("clientDataJSON") byte[] clientDataJSON,
            @JsonProperty("attestationObject") byte[] attestationObject) {
        this.clientDataJSON = clientDataJSON;
        this.attestationObject = attestationObject;
    }

    public AuthenticatorAttestationResponse() {
    }

    public byte[] getClientDataJSON() {
        return clientDataJSON;
    }

    public void setClientDataJSON(byte[] clientDataJSON) {
        this.clientDataJSON = clientDataJSON;
    }

    public byte[] getAttestationObject() {
        return attestationObject;
    }

    public void setAttestationObject(byte[] attestationObject) {
        this.attestationObject = attestationObject;
    }
}
