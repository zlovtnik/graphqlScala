package com.example.ssf.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Map<String, String> CLIENT_MESSAGES = Map.of(
            "USERNAME_BLANK", "Username is required",
            "EMAIL_BLANK", "Email is required",
            "EMAIL_INVALID", "Email format is invalid",
            "USERNAME_IN_USE", "Username already exists",
            "EMAIL_IN_USE", "Email already exists",
            "PASSWORD_BLANK", "Password is required",
            "PASSWORD_ENCODED", "Password must be provided unhashed; ensure it is sent over a secure channel (e.g., HTTPS)",
            "PASSWORD_TOO_SHORT", "Password must be at least 8 characters long",
            "USER_NOT_FOUND", "User not found"
    );
    private static final String DEFAULT_MESSAGE = "Invalid request";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        LOGGER.warn("Illegal argument received", ex);
        String rawMessage = ex.getMessage();
        String clientMessage = CLIENT_MESSAGES.getOrDefault(rawMessage, DEFAULT_MESSAGE);
        return ResponseEntity.badRequest().body(Map.of("error", clientMessage));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, List<String>>> handleValidationException(MethodArgumentNotValidException ex) {
        LOGGER.warn("Validation failed", ex);
        List<String> errors = ex.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .toList();
        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }
}
