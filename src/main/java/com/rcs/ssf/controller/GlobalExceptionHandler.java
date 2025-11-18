package com.rcs.ssf.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
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
    private final MessageSource messageSource;
    private static final String DEFAULT_MESSAGE = "Invalid request";

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        LOGGER.warn("Illegal argument received", ex);
        String exceptionMessage = ex.getMessage();
        String clientMessage;
        var locale = LocaleContextHolder.getLocale();

        // Try to resolve the exception message as a message key, falling back to the default message
        if (exceptionMessage != null) {
            try {
                clientMessage = messageSource.getMessage(exceptionMessage, null, locale);
            } catch (NoSuchMessageException e) {
                // Message key not found in message source, use the default message for security
                LOGGER.warn("No i18n message found for key: {}", exceptionMessage);
                clientMessage = DEFAULT_MESSAGE;
            }
        } else {
            clientMessage = DEFAULT_MESSAGE;
        }

        return ResponseEntity.badRequest().body(Map.of("error", clientMessage));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, List<String>>> handleValidationException(MethodArgumentNotValidException ex) {
        LOGGER.warn("Validation failed", ex);
        var locale = LocaleContextHolder.getLocale();
        List<String> errors = ex.getBindingResult().getAllErrors().stream()
            .map(error -> messageSource.getMessage(Objects.requireNonNull(error), locale))
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(message -> !message.isBlank())
            .toList();
        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }
}
