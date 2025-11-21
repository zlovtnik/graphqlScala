package com.rcs.ssf.util;

import java.util.regex.Pattern;

/**
 * Utility for redacting Personally Identifiable Information (PII) from log
 * messages.
 * Complies with GDPR/CCPA requirements by masking sensitive data in application
 * logs.
 * 
 * Note: This utility is for application-level logging only. Audit logs with
 * full PII
 * are stored separately in access-controlled, encrypted audit tables with
 * restricted retention.
 */
public class PiiRedactionUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final String EMAIL_REDACTION = "[email redacted]";
    private static final String USERNAME_REDACTION = "[username redacted]";

    /**
     * Redacts email addresses from a string.
     * Replaces email patterns with a generic placeholder.
     *
     * @param input the input string potentially containing email addresses
     * @return the string with email addresses redacted
     */
    public static String redactEmail(String input) {
        if (input == null) {
            return null;
        }
        return EMAIL_PATTERN.matcher(input).replaceAll(EMAIL_REDACTION);
    }

    /**
     * Redacts username from a string by replacing it with a placeholder.
     * This is a simple replacement; for more complex scenarios, consider using a
     * hash.
     *
     * @param input    the input string potentially containing the username
     * @param username the username to redact
     * @return the string with the username redacted
     */
    public static String redactUsername(String input, String username) {
        if (input == null || username == null) {
            return input;
        }
        // Case-insensitive replacement to catch variations
        return input.replaceAll("(?i)" + Pattern.quote(username), USERNAME_REDACTION);
    }

    /**
     * Redacts both email and username from a string.
     *
     * @param input    the input string
     * @param username the username to redact
     * @return the string with both email and username redacted
     */
    public static String redactPii(String input, String username) {
        if (input == null) {
            return null;
        }
        String redacted = redactEmail(input);
        redacted = redactUsername(redacted, username);
        return redacted;
    }

    /**
     * Creates a safe, non-identifying reference for a username.
     * Returns a generic placeholder that does not reveal the actual username.
     *
     * @return a safe reference string
     */
    public static String getSafeUserReference() {
        return "[user]";
    }

    /**
     * Creates a safe, non-identifying reference for an email.
     * Returns a generic placeholder that does not reveal the actual email.
     *
     * @return a safe reference string
     */
    public static String getSafeEmailReference() {
        return "[email]";
    }
}
