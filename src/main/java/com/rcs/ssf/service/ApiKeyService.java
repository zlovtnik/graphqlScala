package com.rcs.ssf.service;

import com.rcs.ssf.dto.ApiKeyDto;
import com.rcs.ssf.dto.CreateApiKeyRequestDto;
import com.rcs.ssf.dto.CreateApiKeyResponseDto;
import com.rcs.ssf.entity.ApiKey;
import com.rcs.ssf.repository.ApiKeyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing API key lifecycle.
 * Keys are generated, hashed, and validated against stored hashes.
 */
@Slf4j
@Service
public class ApiKeyService {
    private static final String API_KEY_PREFIX = "sk_";
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(5);
    private static final long DEFAULT_EXPIRY_DAYS = 90;
    private static final long MAX_EXPIRY_DAYS = 365 * 10; // 10 years max

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    public ApiKeyService(ApiKeyRepository apiKeyRepository, PasswordEncoder passwordEncoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Generate a new API key for a user (reactive).
     * Validates expiry days and computes key preview before hashing.
     * Returns a Mono that emits the response without blocking.
     * 
     * @param userId the user ID
     * @param request the API key creation request
     * @return Mono emitting CreateApiKeyResponseDto on success
     */
    public Mono<CreateApiKeyResponseDto> generateApiKey(Long userId, CreateApiKeyRequestDto request) {
        log.debug("Generating new API key for user: {}", userId);

        // Validate expiry days
        long expiryDays = validateExpiryDays(request.getExpiresInDays());

        // Generate raw key (only shown once)
        String rawKey = generateRawKey();
        
        // Compute preview BEFORE hashing (from raw key)
        String keyPreview = maskKey(rawKey);
        
        // Hash the raw key for storage
        String keyHash = passwordEncoder.encode(rawKey);

        // Calculate expiration timestamp
        long expiresAt = System.currentTimeMillis() + (expiryDays * 24 * 60 * 60 * 1000);

        // Create and persist API key with preview reactively
        ApiKey apiKey = new ApiKey(userId, request.getKeyName(), keyHash, keyPreview, expiresAt);
        return apiKeyRepository.save(apiKey)
                .timeout(OPERATION_TIMEOUT)
                .doOnSuccess(saved -> log.info("API key '{}' generated for user: {}", request.getKeyName(), userId))
                .map(saved -> new CreateApiKeyResponseDto(
                        saved.getId(),
                        saved.getKeyName(),
                        rawKey,
                        keyPreview,
                        saved.getExpiresAt(),
                        saved.getCreatedAt(),
                        "⚠️ Save this key somewhere safe. You won't be able to see it again."
                ));
    }

    /**
     * List all API keys for a user (non-sensitive info only, reactive).
     * Returns a Mono emitting a list without blocking.
     * 
     * @param userId the user ID
     * @return Mono emitting list of ApiKeyDto
     */
    public Mono<List<ApiKeyDto>> listApiKeysForUser(Long userId) {
        log.debug("Listing API keys for user: {}", userId);
        return apiKeyRepository.findByUserId(userId)
                .timeout(OPERATION_TIMEOUT)
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * List all active API keys for a user (reactive).
     * Returns a Mono emitting a list without blocking.
     * 
     * @param userId the user ID
     * @return Mono emitting list of active ApiKeyDto
     */
    public Mono<List<ApiKeyDto>> listActiveApiKeysForUser(Long userId) {
        long currentTime = System.currentTimeMillis();
        return apiKeyRepository.findActiveByUserId(userId, currentTime)
                .timeout(OPERATION_TIMEOUT)
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Revoke an API key (reactive).
     * Returns a Mono that emits the revoked key without blocking.
     * 
     * @param userId the user ID
     * @param keyId the API key ID
     * @return Mono emitting the revoked ApiKeyDto
     */
    public Mono<ApiKeyDto> revokeApiKey(Long userId, Long keyId) {
        log.debug("Revoking API key {} for user: {}", keyId, userId);

        if (keyId == null) {
            return Mono.error(new IllegalArgumentException("API key ID cannot be null"));
        }

        return apiKeyRepository.findById(keyId)
                .timeout(OPERATION_TIMEOUT)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("API key not found: " + keyId)))
                .flatMap(apiKey -> {
                    // Verify ownership
                    if (!apiKey.getUserId().equals(userId)) {
                        return Mono.error(new IllegalArgumentException("User not authorized to revoke this key"));
                    }

                    apiKey.setRevokedAt(System.currentTimeMillis());
                    apiKey.setUpdatedAt(System.currentTimeMillis());

                    return apiKeyRepository.save(apiKey)
                            .timeout(OPERATION_TIMEOUT)
                            .doOnSuccess(revoked -> log.info("API key {} revoked for user: {}", keyId, userId))
                            .map(this::mapToDto);
                });
    }

    /**
     * Delete an API key (hard delete, reactive).
     * Returns a Mono that completes when delete is finished without blocking.
     * 
     * @param userId the user ID
     * @param keyId the API key ID
     * @return Mono that completes on success
     */
    public Mono<Void> deleteApiKey(Long userId, Long keyId) {
        log.debug("Deleting API key {} for user: {}", keyId, userId);

        if (keyId == null) {
            return Mono.error(new IllegalArgumentException("API key ID cannot be null"));
        }

        return apiKeyRepository.findById(keyId)
                .timeout(OPERATION_TIMEOUT)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("API key not found: " + keyId)))
                .flatMap(apiKey -> {
                    // Verify ownership
                    if (!apiKey.getUserId().equals(userId)) {
                        return Mono.error(new IllegalArgumentException("User not authorized to delete this key"));
                    }

                    return apiKeyRepository.deleteById(keyId)
                            .timeout(OPERATION_TIMEOUT)
                            .doOnSuccess(v -> log.info("API key {} deleted for user: {}", keyId, userId));
                });
    }

    /**
     * Validate an API key by checking if the raw key matches a stored hash.
     * Uses reactive flows without blocking. Returns the userId if valid, empty otherwise.
     * 
     * Note: This method is O(n) over active keys because BCrypt hashes cannot be compared
     * before hashing. For high-traffic scenarios, consider storing a hash prefix in a separate column.
     */
    public Mono<Long> validateApiKeyReactive(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(API_KEY_PREFIX)) {
            return Mono.empty();
        }

        long currentTime = System.currentTimeMillis();
        
        // Fetch all active keys and find matching hash using passwordEncoder.matches()
        return apiKeyRepository.findAllActive(currentTime)
                .filter(apiKey -> passwordEncoder.matches(rawKey, apiKey.getKeyHash()))
                .next()  // Take first match
                .flatMap(apiKey -> {
                    // Update last used timestamp reactively
                    apiKey.setLastUsedAt(System.currentTimeMillis());
                    apiKey.setUpdatedAt(System.currentTimeMillis());
                    return apiKeyRepository.save(apiKey)
                            .timeout(OPERATION_TIMEOUT)
                            .map(saved -> apiKey.getUserId());
                })
                .timeout(OPERATION_TIMEOUT);
    }

    /**
     * Validate an API key by checking if the raw key matches a stored hash.
     * 
     * DEPRECATED: Use validateApiKeyReactive(String) instead for non-blocking operations.
     * This blocking implementation causes thread exhaustion under load and should not be used
     * in any production code path. It is kept only for legacy backward compatibility.
     * 
     * Removal timeline: Will be removed in next major version (2.0).
     * Migration guide: Replace all calls with validateApiKeyReactive(String) and handle
     * the returned Mono in the calling controller/resolver instead of blocking.
     * 
     * Performance note: This method is O(n) over active keys and blocks the calling thread.
     * Critical issues in old implementation:
     * - Called findByKeyHash(rawKey) expecting hashed value (but rawKey is not hashed)
     * - Hashes from passwordEncoder.encode() can never match raw input
     * - Blocked inside map operator causing reactor thread exhaustion
     * 
     * @param rawKey the raw API key to validate
     * @return Optional containing userId if valid, empty if invalid
     * @deprecated Use validateApiKeyReactive(String) instead
     */
    @Deprecated(since = "1.1", forRemoval = true)
    public Optional<Long> validateApiKey(String rawKey) {
        log.warn("DEPRECATED: validateApiKey(String) called - blocking and inefficient. " +
                "Use validateApiKeyReactive(String) instead and handle Mono in web layer.");
        
        if (rawKey == null || !rawKey.startsWith(API_KEY_PREFIX)) {
            return Optional.empty();
        }
        
        long currentTime = System.currentTimeMillis();
        
        return apiKeyRepository.findAllActive(currentTime)
                .filter(apiKey -> passwordEncoder.matches(rawKey, apiKey.getKeyHash()))
                .next()
                .timeout(OPERATION_TIMEOUT)
                .blockOptional()
                .map(apiKey -> {
                    // Update last used timestamp reactively
                    apiKey.setLastUsedAt(System.currentTimeMillis());
                    apiKey.setUpdatedAt(System.currentTimeMillis());
                    // Save reactively but don't block - fire and forget for non-critical timestamp update
                    apiKeyRepository.save(apiKey)
                            .timeout(OPERATION_TIMEOUT)
                            .subscribe(
                                    saved -> log.debug("Updated last used timestamp for API key"),
                                    error -> log.warn("Failed to update last used timestamp for API key", error)
                            );
                    return apiKey.getUserId();
                });
    }

    /**
     * Check if an API key is currently active (not revoked and not expired).
     * Accepts an injected Clock to allow testing with arbitrary time values.
     * 
     * @param keyDto the API key DTO to check
     * @param clock the clock to get current time from (e.g., Clock.systemDefaultZone())
     * @return true if key is active, false if revoked or expired
     */
    public boolean isApiKeyActive(ApiKeyDto keyDto, java.time.Clock clock) {
        if (keyDto == null) {
            return false;
        }
        boolean isNotRevoked = keyDto.getRevokedAt() == null;
        boolean isNotExpired = keyDto.getExpiresAt() == null || clock.millis() < keyDto.getExpiresAt();
        return isNotRevoked && isNotExpired;
    }

    /**
     * Generate a raw API key (format: sk_<random>).
     */
    private String generateRawKey() {
        return API_KEY_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Mask API key for display (show first 8 and last 4 chars).
     */
    private String maskKey(String rawKey) {
        if (rawKey == null || rawKey.length() < 12) {
            return "***";
        }
        String prefix = rawKey.substring(0, 8);
        String suffix = rawKey.substring(rawKey.length() - 4);
        return prefix + "..." + suffix;
    }

    /**
     * Validate expiry days value.
     * Ensures value is positive and within acceptable bounds.
     */
    private long validateExpiryDays(Long expiryDays) {
        if (expiryDays == null || expiryDays <= 0) {
            log.debug("Invalid expiry days: {}, using default: {}", expiryDays, DEFAULT_EXPIRY_DAYS);
            return DEFAULT_EXPIRY_DAYS;
        }
        if (expiryDays > MAX_EXPIRY_DAYS) {
            log.warn("Expiry days {} exceeds maximum {}, capping to maximum", expiryDays, MAX_EXPIRY_DAYS);
            return MAX_EXPIRY_DAYS;
        }
        return expiryDays;
    }

    private ApiKeyDto mapToDto(ApiKey entity) {
        if (entity == null) {
            return null;
        }
        ApiKeyDto dto = new ApiKeyDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setKeyName(entity.getKeyName());
        // Use stored key preview instead of masking the hash
        dto.setKeyPreview(entity.getKeyPreview() != null ? entity.getKeyPreview() : "sk_••••••••");
        dto.setLastUsedAt(entity.getLastUsedAt());
        dto.setRevokedAt(entity.getRevokedAt());
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
