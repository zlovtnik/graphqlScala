package com.rcs.ssf.batch;

/**
 * Exception thrown when a batch operation fails.
 */
public class BatchOperationException extends RuntimeException {

    public BatchOperationException(String message) {
        super(message);
    }

    public BatchOperationException(String message, Throwable cause) {
        super(message, cause);
    }

}
