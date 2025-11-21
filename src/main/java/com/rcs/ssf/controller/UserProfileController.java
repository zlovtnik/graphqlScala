package com.rcs.ssf.controller;

import com.rcs.ssf.dto.AvatarUploadResponseDto;
import com.rcs.ssf.entity.User;
import com.rcs.ssf.repository.UserRepository;
import com.rcs.ssf.service.AvatarUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.io.InputStream;

/**
 * REST controller for user profile endpoints.
 * Handles avatar uploads, password changes, and other profile operations.
 *
 * CORS handling: Uses centralized SecurityConfig bean (corsConfigurationSource)
 * with allowed origins from app.cors.allowed-origins property.
 * Do not add @CrossOrigin annotation to maintain centralized security control.
 */
@RestController
@RequestMapping("/api/user")
public class UserProfileController {

  private static final Logger logger = LoggerFactory.getLogger(UserProfileController.class);

  private final AvatarUploadService avatarUploadService;
  private final UserRepository userRepository;

  public UserProfileController(
      AvatarUploadService avatarUploadService,
      UserRepository userRepository
  ) {
    this.avatarUploadService = avatarUploadService;
    this.userRepository = userRepository;
  }

  /**
   * Upload user avatar to MinIO.
   * Accepts JPEG, PNG, and WebP formats, max 5MB.
   *
   * @param file Avatar file to upload
   * @return Avatar upload response with key and URL
   */
  @PostMapping("/avatar")
  public Mono<ResponseEntity<AvatarUploadResponseDto>> uploadAvatar(
      @RequestParam("file") MultipartFile file
  ) {
    Long userId = getCurrentUserId();
    if (userId == null) {
      return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    logger.info("Uploading avatar for user: {}", userId);

    // Wrap blocking upload call in Mono.fromCallable with boundedElastic scheduler
    return Mono.fromCallable(() -> avatarUploadService.uploadAvatar(userId, file))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(avatarKey -> 
            // Update user's avatarKey reactively
            userRepository.findById(userId)
                .flatMap(user -> {
                  user.setAvatarKey(avatarKey);
                  return userRepository.save(user);
                })
                .map(user -> {
                  logger.info("Avatar uploaded successfully for user: {} with key: {}", userId, avatarKey);
                  AvatarUploadResponseDto response = new AvatarUploadResponseDto(
                      avatarKey,
                      "/api/user/avatar/" + avatarKey,
                      "Avatar uploaded successfully"
                  );
                  return ResponseEntity.ok(response);
                })
        )
        .doOnError(e -> logger.error("Error uploading avatar for user: {}", userId, e))
        .onErrorResume(e -> Mono.just(
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        ));
  }

  /**
   * Download user avatar by avatar key.
   * Streams avatar data non-blockingly using Mono.fromCallable with bounded elastic scheduler.
   *
   * @param avatarKey Avatar key from MinIO
   * @return Avatar file as byte array with appropriate content type
   */
  @GetMapping("/avatar/{avatarKey}")
  public Mono<ResponseEntity<?>> downloadAvatar(
      @PathVariable String avatarKey
  ) {
    logger.info("Downloading avatar with key: {}", avatarKey);

    // Wrap blocking I/O operations in Mono.fromCallable and schedule on boundedElastic
    return Mono.fromCallable(() -> {
          java.util.Optional<InputStream> avatarDataOpt = avatarUploadService.downloadAvatar(avatarKey);
          
          if (avatarDataOpt.isEmpty()) {
            return null;  // Will be converted to notFound below
          }

          try (InputStream is = avatarDataOpt.get()) {
            // Read all bytes with try-with-resources for automatic cleanup
            return is.readAllBytes();
          }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .map(avatarData -> {
          if (avatarData == null) {
            logger.warn("Avatar not found for key: {}", avatarKey);
            return (ResponseEntity<?>) ResponseEntity.notFound().build();
          }

          // Determine content type from avatarKey extension (non-blocking)
          String contentType = determineContentType(avatarKey);

          logger.info("Avatar downloaded successfully for key: {}", avatarKey);

          return (ResponseEntity<?>) ResponseEntity.ok()
              .header("Content-Type", contentType)
              .header("Cache-Control", "public, max-age=86400")
              .body(avatarData);
        })
        .doOnError(e -> logger.error("Error downloading avatar for key: {}", avatarKey, e))
        .onErrorResume(e -> Mono.just(
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        ));
  }

  /**
   * Delete user avatar.
   * Only the owner can delete their own avatar.
   * Performs deletion reactively with proper error handling.
   *
   * @return Deletion confirmation or error status
   */
  @DeleteMapping("/avatar")
  public Mono<ResponseEntity<?>> deleteAvatar() {
    Long userId = getCurrentUserId();
    if (userId == null) {
      return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    logger.info("Deleting avatar for user: {}", userId);

    // Load user reactively to obtain avatarKey
    return userRepository.findById(userId)
        .flatMap(user -> {
          String avatarKey = user.getAvatarKey();
          
          // If no avatar exists, return success (idempotent delete)
          if (avatarKey == null || avatarKey.isEmpty()) {
            logger.info("No avatar found for user: {}", userId);
            return Mono.just((ResponseEntity<?>) ResponseEntity.ok(java.util.Map.of("message", "No avatar to delete")));
          }

          // Delete avatar from MinIO using boundedElastic scheduler
          return Mono.fromCallable(() -> {
                avatarUploadService.deleteAvatar(avatarKey);
                return avatarKey;  // Return the key for subsequent operations
              })
              .subscribeOn(Schedulers.boundedElastic())
              .flatMap(deletedKey -> {
                // Clear avatarKey from user and save reactively
                user.setAvatarKey(null);
                return userRepository.save(user)
                    .map(updatedUser -> (ResponseEntity<?>) ResponseEntity.ok(java.util.Map.of("message", "Avatar deleted successfully")));
              });
        })
        .doOnError(e -> logger.error("Error deleting avatar for user: {}", userId, e))
        .onErrorResume(e -> {
          // Map exceptions to appropriate HTTP status codes
          if (e.getMessage() != null && e.getMessage().contains("not found")) {
            return Mono.just((ResponseEntity<?>) ResponseEntity.status(HttpStatus.NOT_FOUND).build());
          }
          return Mono.just((ResponseEntity<?>) ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        })
        .switchIfEmpty(Mono.just((ResponseEntity<?>) ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()));
  }

  /**
   * Extract current user ID from SecurityContext.
   *
   * @return User ID or null if not authenticated
   */
  private Long getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }

    Object principal = authentication.getPrincipal();
    if (principal instanceof User) {
      return ((User) principal).getId();
    }

    // Try getting from authentication name as fallback
    try {
      return Long.parseLong(authentication.getName());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Determine content type from file extension.
   *
   * @param filename File name or key
   * @return Content type string
   */
  private String determineContentType(String filename) {
    if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
      return "image/jpeg";
    } else if (filename.endsWith(".png")) {
      return "image/png";
    } else if (filename.endsWith(".webp")) {
      return "image/webp";
    }
    return "application/octet-stream";
  }

  /**
   * Simple message response DTO.
   */
  public record MessageResponseDto(String message) {}
}
