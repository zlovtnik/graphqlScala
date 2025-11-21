package com.rcs.ssf.graphql;

import com.rcs.ssf.dto.ApiKeyDto;
import com.rcs.ssf.dto.CreateApiKeyRequestDto;
import com.rcs.ssf.dto.CreateApiKeyResponseDto;
import com.rcs.ssf.dto.DeactivationReasonDto;
import com.rcs.ssf.dto.UserPreferencesDto;
import com.rcs.ssf.security.AuthenticatedUser;
import com.rcs.ssf.service.AccountService;
import com.rcs.ssf.service.ApiKeyService;
import com.rcs.ssf.service.UserPreferencesService;
import com.rcs.ssf.service.UserService;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * GraphQL controller for user settings operations.
 * Handles preferences, API keys, and account management.
 * All reactive operations return Mono/Flux and delegate to reactive services.
 */
@Slf4j
@Controller
@Validated
public class SettingsMutation {

    private final UserPreferencesService userPreferencesService;
    private final ApiKeyService apiKeyService;
    private final AccountService accountService;
    private final UserService userService;

    public SettingsMutation(UserPreferencesService userPreferencesService,
            ApiKeyService apiKeyService,
            AccountService accountService,
            UserService userService) {
        this.userPreferencesService = userPreferencesService;
        this.apiKeyService = apiKeyService;
        this.accountService = accountService;
        this.userService = userService;
    }

    /**
     * Query: Get current user's preferences.
     */
    @QueryMapping
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 })
    public UserPreferencesDto getUserPreferences() {
        Long userId = getCurrentUserId();
        log.debug("Fetching preferences for user: {}", userId);
        return userPreferencesService.getPreferencesByUserId(userId);
    }

    /**
     * Query: Get API keys for current user (reactive).
     * Returns Mono that emits list of API keys.
     */
    @QueryMapping
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 })
    public Mono<List<ApiKeyDto>> getApiKeys() {
        Long userId = getCurrentUserId();
        log.debug("Fetching API keys for user: {}", userId);
        return apiKeyService.listApiKeysForUser(userId);
    }

    /**
     * Query: Get account status for current user.
     * Fully reactive resolver that returns Mono<String> without explicit blocking.
     * GraphQL transport layer handles the Mono subscription and emission.
     */
    @QueryMapping
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 })
    public Mono<String> getAccountStatus() {
        Long userId = getCurrentUserId();
        log.debug("Fetching account status for user: {}", userId);
        return accountService.getAccountStatus(userId)
                .map(dto -> dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
    }

    /**
     * Mutation: Update user preferences.
     */
    @MutationMapping
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 })
    public UserPreferencesDto updateUserPreferences(@Argument UserPreferencesInput input) {
        Long userId = getCurrentUserId();
        log.debug("Updating preferences for user: {}", userId);

        UserPreferencesDto dto = new UserPreferencesDto();
        dto.setTheme(input.theme());
        dto.setLanguage(input.language());
        dto.setNotificationEmails(input.notificationEmails());
        dto.setNotificationPush(input.notificationPush());
        dto.setNotificationLoginAlerts(input.notificationLoginAlerts());
        dto.setNotificationSecurityUpdates(input.notificationSecurityUpdates());

        return userPreferencesService.updatePreferences(userId, dto);
    }

    /**
     * Mutation: Generate new API key (reactive).
     * Returns Mono that emits the generated API key response.
     */
    @MutationMapping
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 })
    public Mono<CreateApiKeyResponseDto> generateApiKey(@Argument GenerateApiKeyInput input) {
        Long userId = getCurrentUserId();
        log.debug("Generating API key for user: {}", userId);

        CreateApiKeyRequestDto request = new CreateApiKeyRequestDto();
        request.setKeyName(input.keyName());
        request.setExpiresInDays(input.expiresInDays());
        request.setDescription(input.description());

        return apiKeyService.generateApiKey(userId, request);
    }

    /**
     * Mutation: Revoke API key (reactive).
     * Returns Mono that emits the revoked API key.
     */
    @MutationMapping
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 })
    public Mono<ApiKeyDto> revokeApiKey(@Argument Long keyId) {
        Long userId = getCurrentUserId();
        log.debug("Revoking API key {} for user: {}", keyId, userId);
        return apiKeyService.revokeApiKey(userId, keyId);
    }

    /**
     * Mutation: Delete API key (reactive).
     * Returns Mono that completes when deletion is done.
     * Propagates expected exceptions (e.g., not found, unauthorized) as GraphQL errors
     * instead of swallowing them as false, allowing clients to distinguish user vs server errors.
     */
    @MutationMapping
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 })
    public Mono<Boolean> deleteApiKey(@Argument Long keyId) {
        Long userId = getCurrentUserId();
        log.debug("Deleting API key {} for user: {}", keyId, userId);
        return apiKeyService.deleteApiKey(userId, keyId)
                .then(Mono.just(true));
                // Allow exceptions to propagate for proper error handling
                // Previously: .onErrorReturn(false) swallowed all errors
    }

    /**
     * Mutation: Deactivate account with reason tracking.
     * Accepts optional reasonCode and justification.
     * Creates audit trail for compliance and troubleshooting.
     */
    @MutationMapping
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 })
    public Mono<AccountStatusResponse> deactivateAccount(
            @Argument String reasonCode,
            @Argument String justification) {
        Long userId = getCurrentUserId();
        log.info("Deactivating account for user: {} - Reason Code: {}", userId, reasonCode);

        DeactivationReasonDto reason = new DeactivationReasonDto();
        reason.setReasonCode(reasonCode != null ? reasonCode : "USER_INITIATED");
        reason.setJustification(justification);

        return accountService.deactivateAccount(userId, reason)
                .map(status -> new AccountStatusResponse(
                        status.getUserId(),
                        status.getStatus(),
                        status.getDeactivatedAt(),
                        "Account successfully deactivated with audit trail created"))
                .doOnError(error -> log.error("Failed to deactivate account for user: {}", userId, error));
    }

    /**
     * Mutation: Reactivate account.
     * Now uses reactive service.
     */
    @MutationMapping
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 })
    public Mono<AccountStatusResponse> reactivateAccount() {
        Long userId = getCurrentUserId();
        log.info("Reactivating account for user: {}", userId);

        return accountService.reactivateAccount(userId)
                .map(status -> new AccountStatusResponse(
                        status.getUserId(),
                        status.getStatus(),
                        status.getDeactivatedAt(),
                        "Account successfully reactivated"))
                .doOnError(error -> log.error("Failed to reactivate account for user: {}", userId, error));
    }

    /**
     * Mutation: Update password.
     */
    @MutationMapping
    @Timed(value = "graphql.resolver.duration", percentiles = { 0.5, 0.95, 0.99 })
    public Boolean updatePassword(@Argument String currentPassword, @Argument String newPassword) {
        Long userId = getCurrentUserId();
        log.debug("Password update requested for user: {}", userId);
        try {
            return userService.updatePassword(userId, currentPassword, newPassword);
        } catch (IllegalArgumentException e) {
            log.warn("Password update failed for user: {} - {}", userId, e.getMessage());
            throw new IllegalArgumentException("Password update failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during password update for user: {}", userId, e);
            throw new RuntimeException("Password update failed due to an unexpected error");
        }
    }

    /**
     * Get current authenticated user ID.
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser) {
            AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
            return user.getId();
        }
        throw new IllegalStateException("User not authenticated");
    }

    // Input and Output record classes
    public record UserPreferencesInput(
            String theme,
            String language,
            Boolean notificationEmails,
            Boolean notificationPush,
            Boolean notificationLoginAlerts,
            Boolean notificationSecurityUpdates
    ) {}

    public record GenerateApiKeyInput(
            String keyName,
            Long expiresInDays,
            String description
    ) {}

    public record AccountStatusResponse(
            Long userId,
            String status,
            Long deactivatedAt,
            String message
    ) {}
}
