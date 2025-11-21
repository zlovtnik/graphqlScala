package com.rcs.ssf.dto;

/**
 * Response DTO for avatar upload operations.
 */
public class AvatarUploadResponseDto {
  private String avatarKey;
  private String avatarUrl;
  private String message;

  public AvatarUploadResponseDto() {}

  public AvatarUploadResponseDto(String avatarKey, String avatarUrl, String message) {
    this.avatarKey = avatarKey;
    this.avatarUrl = avatarUrl;
    this.message = message;
  }

  public String getAvatarKey() {
    return avatarKey;
  }

  public void setAvatarKey(String avatarKey) {
    this.avatarKey = avatarKey;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @Override
  public String toString() {
    return "AvatarUploadResponseDto{" +
        "avatarKey='" + avatarKey + '\'' +
        ", avatarUrl='" + avatarUrl + '\'' +
        ", message='" + message + '\'' +
        '}';
  }
}
