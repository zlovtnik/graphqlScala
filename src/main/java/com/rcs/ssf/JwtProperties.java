package com.rcs.ssf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
@Component
public class JwtProperties {
    @NotBlank(message = "app.jwt.secret must not be blank")
    @Size(min = 32, max = 512, message = "app.jwt.secret must be between {min} and {max} characters")
    private String secret;

    @JsonIgnore
    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    @Override
    public String toString() {
        return "JwtProperties{secret='[PROTECTED]'}";
    }
}
