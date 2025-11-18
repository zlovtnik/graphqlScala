package com.rcs.ssf.security.mfa.authentication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * WebAuthn authentication response from the client.
 * Contains the credential ID, raw ID, assertion response, and credential type.
 *
 * This is the complete response returned by navigator.credentials.get() on the client side,
 * and should be sent to the server for verification.
 *
 * Per WebAuthn spec (https://www.w3.org/TR/webauthn-2/#dictionary-response):
 * - id: Text encoding of the credential ID (typically base64url)
 * - rawId: Binary encoding of the credential ID
 * - response: AuthenticatorAssertionResponse with signature and client data
 * - type: Must be "public-key"
 */
public class WebAuthnAuthenticationResponse {
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
