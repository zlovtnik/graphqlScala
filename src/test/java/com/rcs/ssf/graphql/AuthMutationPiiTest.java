package com.rcs.ssf.graphql;

import com.rcs.ssf.util.PiiRedactionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PII redaction in AuthMutation.
 * Verifies that application logs do not contain raw username or email
 * addresses,
 * complying with GDPR/CCPA requirements.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AuthMutation PII Redaction Tests")
class AuthMutationPiiTest {

    private String testUsername;
    private String testEmail;
    private String testErrorMessage;

    @BeforeEach
    void setUp() {
        testUsername = "john.doe";
        testEmail = "john.doe@example.com";
        testErrorMessage = "User with username john.doe already exists. Email john.doe@example.com is in use.";
    }

    @Test
    @DisplayName("Should redact email addresses from log messages")
    void testEmailRedaction() {
        // Given
        String messageWithEmail = "Registration failed for " + testEmail;

        // When
        String redacted = PiiRedactionUtil.redactEmail(messageWithEmail);

        // Then
        assertFalse(redacted.contains(testEmail), "Email should be redacted from log message");
        assertTrue(redacted.contains("[email redacted]"), "Email should be replaced with placeholder");
        assertFalse(redacted.contains("@"), "No email patterns should remain");
    }

    @Test
    @DisplayName("Should redact usernames from log messages")
    void testUsernameRedaction() {
        // Given
        String messageWithUsername = "Registration failed for user " + testUsername;

        // When
        String redacted = PiiRedactionUtil.redactUsername(messageWithUsername, testUsername);

        // Then
        assertFalse(redacted.contains(testUsername), "Username should be redacted from log message");
        assertTrue(redacted.contains("[username redacted]"), "Username should be replaced with placeholder");
    }

    @Test
    @DisplayName("Should redact both email and username from log messages")
    void testFullPiiRedaction() {
        // Given
        String messageWithPii = testErrorMessage;

        // When
        String redacted = PiiRedactionUtil.redactPii(messageWithPii, testUsername);

        // Then
        assertFalse(redacted.contains(testUsername), "Username should be redacted");
        assertFalse(redacted.contains(testEmail), "Email should be redacted");
        assertTrue(redacted.contains("[username redacted]"), "Username should be replaced");
        assertTrue(redacted.contains("[email redacted]"), "Email should be replaced");
    }

    @Test
    @DisplayName("Should handle null input gracefully")
    void testNullInputHandling() {
        // When/Then
        assertNull(PiiRedactionUtil.redactEmail(null), "Should return null for null email input");
        assertNull(PiiRedactionUtil.redactPii(null, testUsername), "Should return null for null PII input");
        assertEquals("test", PiiRedactionUtil.redactUsername("test", null), "Should return original for null username");
    }

    @Test
    @DisplayName("Should handle case-insensitive username redaction")
    void testCaseInsensitiveUsernameRedaction() {
        // Given
        String messageWithUppercaseUsername = "Registration failed for user " + testUsername.toUpperCase();

        // When
        String redacted = PiiRedactionUtil.redactUsername(messageWithUppercaseUsername, testUsername);

        // Then
        assertFalse(redacted.toUpperCase().contains(testUsername.toUpperCase()),
                "Username should be redacted regardless of case");
    }

    @Test
    @DisplayName("Should preserve non-PII information in redacted messages")
    void testNonPiiPreservation() {
        // Given
        String messageWithContext = "Registration failed: " + testErrorMessage + " from IP 192.168.1.1";

        // When
        String redacted = PiiRedactionUtil.redactPii(messageWithContext, testUsername);

        // Then
        assertTrue(redacted.contains("Registration failed"), "Non-PII context should be preserved");
        assertTrue(redacted.contains("192.168.1.1"), "IP address should be preserved");
        assertFalse(redacted.contains(testUsername), "Username should still be redacted");
        assertFalse(redacted.contains(testEmail), "Email should still be redacted");
    }

    @Test
    @DisplayName("Should provide safe user reference")
    void testSafeUserReference() {
        // When
        String safeRef = PiiRedactionUtil.getSafeUserReference();

        // Then
        assertNotNull(safeRef, "Safe reference should not be null");
        assertFalse(safeRef.isEmpty(), "Safe reference should not be empty");
        assertTrue(safeRef.contains("[user]"), "Safe reference should be generic");
    }

    @Test
    @DisplayName("Should provide safe email reference")
    void testSafeEmailReference() {
        // When
        String safeRef = PiiRedactionUtil.getSafeEmailReference();

        // Then
        assertNotNull(safeRef, "Safe reference should not be null");
        assertFalse(safeRef.isEmpty(), "Safe reference should not be empty");
        assertTrue(safeRef.contains("[email]"), "Safe reference should be generic");
    }

    @Test
    @DisplayName("Should handle multiple email addresses in message")
    void testMultipleEmailRedaction() {
        // Given
        String messageWithMultipleEmails = "Conflict between " + testEmail + " and admin@example.com";

        // When
        String redacted = PiiRedactionUtil.redactEmail(messageWithMultipleEmails);

        // Then
        assertFalse(redacted.contains("@"), "All email patterns should be redacted");
        assertEquals(2, redacted.split("\\[email redacted\\]").length - 1,
                "Both emails should be redacted");
    }

    @Test
    @DisplayName("Should not redact email-like patterns that are not valid emails")
    void testPartialEmailNotRedacted() {
        // Given
        String messageWithPartialEmail = "User mentioned something@";

        // When
        String redacted = PiiRedactionUtil.redactEmail(messageWithPartialEmail);

        // Then
        assertTrue(redacted.contains("something@"), "Incomplete email patterns should not be redacted");
    }
}
