package com.rcs.ssf.security.mfa;

/**
 * Exceptions for MFA operations.
 */
public class MfaVerificationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MfaVerificationException(String message) {
        super(message);
    }

    public MfaVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

class MfaProviderException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MfaProviderException(String message) {
        super(message);
    }

    public MfaProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
