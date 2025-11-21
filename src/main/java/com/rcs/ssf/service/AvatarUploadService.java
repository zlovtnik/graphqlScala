package com.rcs.ssf.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Service for handling avatar uploads and retrieval from MinIO.
 * Respects MinioConfig for all connection settings.
 */
@Slf4j
@Service
public class AvatarUploadService {
    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_TYPES = { "image/jpeg", "image/png", "image/webp" };

    private final MinioClient minioClient;

    @Value("${app.minio.bucket.avatars:avatars}")
    private String avatarsBucket;

    public AvatarUploadService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * Upload an avatar file to MinIO and return the object key.
     */
    public String uploadAvatar(Long userId, MultipartFile file)
            throws IOException, MinioException, InvalidKeyException, NoSuchAlgorithmException {
        log.debug("Uploading avatar for user: {}", userId);

        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Avatar file cannot be empty");
        }

        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new IllegalArgumentException("Avatar file exceeds maximum size of 5MB");
        }

        if (!isAllowedType(file.getContentType())) {
            throw new IllegalArgumentException("Invalid avatar file type. Allowed: JPEG, PNG, WebP");
        }

        // Generate unique key: user_id_<hash>.<ext>
        String fileExtension = getFileExtension(file.getOriginalFilename());
        String objectKey = generateAvatarKey(userId, fileExtension);

        try (InputStream inputStream = file.getInputStream()) {
            // Upload to MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(avatarsBucket)
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());

            log.info("Avatar uploaded successfully for user {} with key: {}", userId, objectKey);
            return objectKey;
        }
    }

    /**
     * Download avatar from MinIO.
     */
    public Optional<InputStream> downloadAvatar(String objectKey)
            throws MinioException, InvalidKeyException, NoSuchAlgorithmException, IOException {
        log.debug("Downloading avatar: {}", objectKey);

        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(avatarsBucket)
                            .object(objectKey)
                            .build());

            return Optional.of(stream);
        } catch (MinioException e) {
            if (e.getMessage() != null && e.getMessage().contains("The specified key does not exist")) {
                log.warn("Avatar not found: {}", objectKey);
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * Delete avatar from MinIO.
     */
    public void deleteAvatar(String objectKey)
            throws MinioException, InvalidKeyException, NoSuchAlgorithmException, IOException {
        log.debug("Deleting avatar: {}", objectKey);

        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(avatarsBucket)
                        .object(objectKey)
                        .build());

        log.info("Avatar deleted: {}", objectKey);
    }

    /**
     * Generate avatar object key.
     */
    private String generateAvatarKey(Long userId, String extension) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String hash = md5Hash(userId + "_" + timestamp);
        return "user_" + userId + "_" + hash + "." + extension;
    }

    /**
     * Get file extension from filename.
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "jpg";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * Check if MIME type is allowed.
     */
    private boolean isAllowedType(String contentType) {
        if (contentType == null) {
            return false;
        }
        for (String allowed : ALLOWED_TYPES) {
            if (contentType.equalsIgnoreCase(allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generate MD5 hash of a string.
     */
    private String md5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            return HexFormat.of().formatHex(messageDigest);
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 algorithm not available", e);
            return String.valueOf(System.nanoTime());
        }
    }
}
