package com.rcs.ssf.service;

import com.rcs.ssf.dto.AccountStatusDto;
import com.rcs.ssf.dto.DeactivationReasonDto;
import com.rcs.ssf.entity.AccountDeactivationAudit;
import com.rcs.ssf.entity.AccountStatus;
import com.rcs.ssf.entity.User;
import com.rcs.ssf.repository.AccountDeactivationAuditRepository;
import com.rcs.ssf.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Service for managing account status (deactivation/reactivation).
 * All operations are non-blocking and reactive using Project Reactor.
 */
@Slf4j
@Service
public class AccountService {
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(5);

    private final UserRepository userRepository;
    private final AccountDeactivationAuditRepository auditRepository;

    public AccountService(UserRepository userRepository, AccountDeactivationAuditRepository auditRepository) {
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
    }

    /**
     * Deactivate a user account with optional reason tracking.
     * Creates an audit record capturing the deactivation reason.
     */
    public Mono<AccountStatusDto> deactivateAccount(Long userId, DeactivationReasonDto reason) {
        if (userId == null) {
            return Mono.error(new IllegalArgumentException("User ID cannot be null"));
        }

        return userRepository.findById(userId)
                .timeout(OPERATION_TIMEOUT)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .doOnNext(user -> log.debug("Found user for deactivation: {}", userId))
                .flatMap(user -> {
                    // Update user entity
                    user.setAccountStatus(AccountStatus.DEACTIVATED);
                    user.setAccountDeactivatedAt(System.currentTimeMillis());

                    // Create audit record
                    String reasonCode = reason != null && reason.getReasonCode() != null 
                        ? reason.getReasonCode() 
                        : "USER_INITIATED";
                    String justification = reason != null ? reason.getJustification() : null;

                    AccountDeactivationAudit audit = new AccountDeactivationAudit(
                            userId,
                            reasonCode,
                            justification
                    );

                    // Save both user and audit record in parallel
                    return Mono.zip(
                            userRepository.save(user),
                            auditRepository.save(audit)
                    )
                    .map(tuple -> tuple.getT1()) // Extract user from tuple
                    .timeout(OPERATION_TIMEOUT);
                })
                .doOnSuccess(user -> log.info("Account deactivated for user: {} with reason: {}", 
                        userId, reason != null ? reason.getReasonCode() : "none"))
                .doOnError(error -> log.error("Failed to deactivate account for user: {}", userId, error))
                .map(this::mapToDto);
    }

    /**
     * Deactivate a user account without reason tracking (legacy compatibility).
     */
    public Mono<AccountStatusDto> deactivateAccount(Long userId) {
        return deactivateAccount(userId, null);
    }

    /**
     * Reactivate a user account.
     */
    public Mono<AccountStatusDto> reactivateAccount(Long userId) {
        if (userId == null) {
            return Mono.error(new IllegalArgumentException("User ID cannot be null"));
        }

        return userRepository.findById(userId)
                .timeout(OPERATION_TIMEOUT)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .doOnNext(user -> log.debug("Found user for reactivation: {}", userId))
                .flatMap(user -> {
                    if (user.getAccountStatus() != AccountStatus.DEACTIVATED) {
                        return Mono.error(new IllegalArgumentException(
                                "Cannot reactivate an account that is not deactivated. Current status: " 
                                + user.getAccountStatus()));
                    }

                    user.setAccountStatus(AccountStatus.ACTIVE);
                    user.setAccountDeactivatedAt(null);

                    return userRepository.save(user)
                            .timeout(OPERATION_TIMEOUT);
                })
                .doOnSuccess(user -> log.info("Account reactivated for user: {}", userId))
                .doOnError(error -> log.error("Failed to reactivate account for user: {}", userId, error))
                .map(this::mapToDto);
    }

    /**
     * Get account status reactively.
     */
    public Mono<AccountStatusDto> getAccountStatus(Long userId) {
        if (userId == null) {
            return Mono.error(new IllegalArgumentException("User ID cannot be null"));
        }

        return userRepository.findById(userId)
                .timeout(OPERATION_TIMEOUT)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .doOnNext(user -> log.debug("Fetched account status for user: {}", userId))
                .map(this::mapToDto);
    }

    /**
     * Check if account is active reactively.
     */
    public Mono<Boolean> isAccountActive(Long userId) {
        if (userId == null) {
            return Mono.just(false);
        }

        return userRepository.findById(userId)
                .timeout(OPERATION_TIMEOUT)
                .map(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .onErrorReturn(false)
                .defaultIfEmpty(false);
    }

    private AccountStatusDto mapToDto(User entity) {
        if (entity == null) {
            return null;
        }
        AccountStatusDto dto = new AccountStatusDto();
        dto.setUserId(entity.getId());
        dto.setStatus(entity.getAccountStatus() != null ? entity.getAccountStatus().name() : "ACTIVE");
        dto.setDeactivatedAt(entity.getAccountDeactivatedAt());
        return dto;
    }
}
