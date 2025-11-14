package com.rcs.ssf;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.minio")
@Component
public class MinioProperties {
    @NotBlank(message = "app.minio.url must not be blank and MINIO_URL environment variable must be set")
    @Pattern(regexp = "^https?://.+$", message = "app.minio.url must be a valid HTTP/HTTPS URL")
    private String url;

    @NotBlank(message = "app.minio.access-key must not be blank and MINIO_ACCESS_KEY environment variable must be set")
    private String accessKey;

    @NotBlank(message = "app.minio.secret-key must not be blank and MINIO_SECRET_KEY environment variable must be set")
    private String secretKey;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
