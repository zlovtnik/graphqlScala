package com.example.ssf.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Map<String, String> CLIENT_MESSAGES = Map.of(
            "Username must not be blank", "Username is required",
            "Email must not be blank", "Email is required",
            "Email format is invalid", "Email format is invalid",
            "Username is already in use", "Username already exists",
            "Email is already in use", "Email already exists",
            "Password must not be blank", "Password is required",
<<<<<<< HEAD
            "Password must be provided in raw form", "Password must not be pre-encoded",
            "Password must be at least 8 characters long", "Password must meet length requirements",
=======
            "Password must be provided in raw form", "Password must be provided in plain text",
            "Password must be at least 8 characters long", "Password must be at least 8 characters long",
>>>>>>> 8aa4622 (feat: add validation for bcrypt strength and improve error handling)
            "User not found", "User not found"
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
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
        LOGGER.warn("Validation failed", ex);
        String message = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
