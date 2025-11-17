package com.rcs.ssf.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for cryptographic hashing operations.
 */
public final class HashUtils {

    private HashUtils() {
        // Utility class
    }

    /**
     * Generate SHA-256 hash of the input string and return as hex-encoded string.
     *
     * @param input The input string to hash (must not be null)
     * @return Hex-encoded SHA-256 hash
     * @throws IllegalArgumentException if input is null
     * @throws IllegalStateException if SHA-256 algorithm is not available
     */
    public static String sha256Hex(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest algorithm is required but not available in this JVM", e);
        }
    }

    /**
     * Convert byte array to hex string.
     *
     * @param hash The byte array to convert
     * @return Hex-encoded string
     */
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}