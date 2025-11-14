package com.rcs.ssf.dynamic.streaming;

/**
 * Immutable options describing how a JDBC stream should behave.
 */
public record QueryStreamOptions(String streamName, int fetchSize) {

    public QueryStreamOptions(String streamName, int fetchSize) {
        if (streamName == null || streamName.isBlank()) {
            throw new IllegalArgumentException("streamName must not be null or blank");
        }
        if (fetchSize <= 0) {
            throw new IllegalArgumentException("fetchSize must be greater than 0");
        }
        this.streamName = streamName;
        this.fetchSize = fetchSize;
    }
}
