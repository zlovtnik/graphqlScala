package com.rcs.ssf.exception;

/**
 * Exception thrown when attempting to create or update a user with an email
 * that already exists.
 */
public class EmailAlreadyExistsException extends IllegalArgumentException {
    public static final String ERROR_CODE = "EMAIL_IN_USE";

    public EmailAlreadyExistsException(String message) {
        super(message);
    }

    public EmailAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
