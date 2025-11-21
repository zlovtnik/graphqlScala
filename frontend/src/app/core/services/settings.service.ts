import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Apollo, gql } from 'apollo-angular';
import { Observable, map } from 'rxjs';

/**
 * Settings service for managing user settings via GraphQL.
 * Handles preferences, API keys, account management, and avatar uploads.
 */
@Injectable({
  providedIn: 'root'
})
export class SettingsService {
  private apollo = inject(Apollo);
  private http = inject(HttpClient);

  /**
   * GraphQL query to get current user's preferences
   */
  private GET_USER_PREFERENCES = gql`
    query GetUserPreferences {
      getUserPreferences {
        userId
        theme
        language
        notificationEmails
        notificationPush
        notificationLoginAlerts
        notificationSecurityUpdates
        updatedAt
      }
    }
  `;

  /**
   * GraphQL mutation to update user preferences
   */
  private UPDATE_USER_PREFERENCES = gql`
    mutation UpdateUserPreferences($preferences: UserPreferencesInput!) {
      updateUserPreferences(preferences: $preferences) {
        userId
        theme
        language
        emailNotifications
        pushNotifications
        apiKeyNotifications
        accountActivityNotifications
        updatedAt
      }
    }
  `;

  /**
   * GraphQL query to get user's API keys
   */
  private GET_API_KEYS = gql`
    query GetApiKeys {
      getApiKeys {
        id
        keyName
        keyPreview
        createdAt
        expiresAt
        lastUsedAt
        revokedAt
        status
      }
    }
  `;

  /**
   * GraphQL mutation to generate new API key
   */
  private GENERATE_API_KEY = gql`
    mutation GenerateApiKey($input: GenerateApiKeyInput!) {
      generateApiKey(input: $input) {
        id
        rawKey
        keyPreview
        keyName
        expiresAt
        createdAt
        warning
      }
    }
  `;

  /**
   * GraphQL mutation to revoke an API key
   */
  private REVOKE_API_KEY = gql`
    mutation RevokeApiKey($keyId: Long!) {
      revokeApiKey(keyId: $keyId) {
        id
        revokedAt
        status
      }
    }
  `;

  /**
   * GraphQL mutation to delete an API key
   */
  private DELETE_API_KEY = gql`
    mutation DeleteApiKey($keyId: Long!) {
      deleteApiKey(keyId: $keyId)
    }
  `;

  /**
   * GraphQL query to get account status
   */
  private GET_ACCOUNT_STATUS = gql`
    query GetAccountStatus {
      getAccountStatus {
        userId
        status
        deactivatedAt
      }
    }
  `;

  /**
   * GraphQL mutation to deactivate account
   */
  private DEACTIVATE_ACCOUNT = gql`
    mutation DeactivateAccount {
      deactivateAccount {
        userId
        status
        deactivatedAt
      }
    }
  `;

  /**
   * GraphQL mutation to reactivate account
   */
  private REACTIVATE_ACCOUNT = gql`
    mutation ReactivateAccount {
      reactivateAccount {
        userId
        status
        deactivatedAt
      }
    }
  `;

  /**
   * GraphQL mutation to update password
   */
  private UPDATE_PASSWORD = gql`
    mutation UpdatePassword($currentPassword: String!, $newPassword: String!) {
      updatePassword(currentPassword: $currentPassword, newPassword: $newPassword) {
        success
        message
      }
    }
  `;

  /**
   * Get current user's preferences
   */
  getUserPreferences(): Observable<any> {
    return this.apollo.query<any>({
      query: this.GET_USER_PREFERENCES
    }).pipe(
      map(result => result.data.getUserPreferences)
    );
  }

  /**
   * Update user preferences
   * @param preferences User preferences to update
   */
  updateUserPreferences(preferences: any): Observable<any> {
    return this.apollo.mutate<any>({
      mutation: this.UPDATE_USER_PREFERENCES,
      variables: { preferences }
    }).pipe(
      map(result => result.data?.updateUserPreferences)
    );
  }

  /**
   * Get all API keys for current user
   */
  getApiKeys(): Observable<any[]> {
    return this.apollo.query<any>({
      query: this.GET_API_KEYS
    }).pipe(
      map(result => result.data.getApiKeys || [])
    );
  }

  /**
   * Generate a new API key
   * @param keyName Name of the API key
   * @param expiresInDays Optional expiration in days
   */
  generateApiKey(keyName: string, expiresInDays?: number): Observable<any> {
    return this.apollo.mutate<any>({
      mutation: this.GENERATE_API_KEY,
      variables: {
        input: { keyName, expiresInDays }
      },
      refetchQueries: [{ query: this.GET_API_KEYS }]
    }).pipe(
      map(result => result.data?.generateApiKey)
    );
  }

  /**
   * Revoke an API key
   * @param keyId ID of the API key to revoke
   */
  revokeApiKey(keyId: number): Observable<any> {
    return this.apollo.mutate<any>({
      mutation: this.REVOKE_API_KEY,
      variables: { keyId },
      refetchQueries: [{ query: this.GET_API_KEYS }]
    }).pipe(
      map(result => result.data?.revokeApiKey)
    );
  }

  /**
   * Delete an API key
   * @param keyId ID of the API key to delete
   */
  deleteApiKey(keyId: number): Observable<boolean> {
    return this.apollo.mutate<any>({
      mutation: this.DELETE_API_KEY,
      variables: { keyId },
      refetchQueries: [{ query: this.GET_API_KEYS }]
    }).pipe(
      map(result => result.data?.deleteApiKey ?? false)
    );
  }

  /**
   * Get current account status
   */
  getAccountStatus(): Observable<any> {
    return this.apollo.query<any>({
      query: this.GET_ACCOUNT_STATUS
    }).pipe(
      map(result => result.data.getAccountStatus)
    );
  }

  /**
   * Deactivate current account
   */
  deactivateAccount(): Observable<any> {
    return this.apollo.mutate<any>({
      mutation: this.DEACTIVATE_ACCOUNT
    }).pipe(
      map(result => result.data?.deactivateAccount)
    );
  }

  /**
   * Reactivate current account
   */
  reactivateAccount(): Observable<any> {
    return this.apollo.mutate<any>({
      mutation: this.REACTIVATE_ACCOUNT
    }).pipe(
      map(result => result.data?.reactivateAccount)
    );
  }

  /**
   * Update user password
   * @param currentPassword Current password for verification
   * @param newPassword New password to set
   */
  updatePassword(currentPassword: string, newPassword: string): Observable<any> {
    return this.apollo.mutate<any>({
      mutation: this.UPDATE_PASSWORD,
      variables: { currentPassword, newPassword }
    }).pipe(
      map(result => result.data?.updatePassword)
    );
  }

  /**
   * Upload avatar to MinIO via HTTP endpoint
   * @param file Avatar file to upload
   * @return Observable with upload response containing avatarKey and avatarUrl
   */
  uploadAvatar(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);

    // Use HttpClient to POST multipart form data to the REST endpoint
    return this.http.post<any>('/api/user/avatar', formData).pipe(
      map(response => ({
        success: true,
        avatarKey: response.avatarKey,
        avatarUrl: response.avatarUrl,
        message: response.message
      }))
    );
  }
}
