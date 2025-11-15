package com.rcs.ssf.dynamic.streaming;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for streaming JDBC result sets via server-side cursors.
 */
@ConfigurationProperties(prefix = "query.streaming")
@Validated
public class QueryStreamingProperties {

    @PositiveOrZero
    private int fetchSize = 500;

    @PositiveOrZero
    private int maxFetchSize = 10_000;

    @PositiveOrZero
    private int idleTimeoutSeconds = 30;

    public int getFetchSize() {
        return fetchSize;
    }

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    public int getMaxFetchSize() {
        return maxFetchSize;
    }

    public void setMaxFetchSize(int maxFetchSize) {
        this.maxFetchSize = maxFetchSize;
    }

    public int getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    @PostConstruct
    void validate() {
        if (fetchSize > maxFetchSize) {
            throw new IllegalStateException(String.format(
                "query.streaming.fetch-size (%d) cannot exceed query.streaming.max-fetch-size (%d)", fetchSize, maxFetchSize));
        }
    }
}
