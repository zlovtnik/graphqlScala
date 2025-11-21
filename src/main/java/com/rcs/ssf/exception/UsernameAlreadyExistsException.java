package com.rcs.ssf.exception;

/**
 * Exception thrown when attempting to create or update a user with a username
 * that already exists.
 */
public class UsernameAlreadyExistsException extends IllegalArgumentException {
    public static final String ERROR_CODE = "USERNAME_IN_USE";

    public UsernameAlreadyExistsException(String message) {
        super(message);
    }

    public UsernameAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
