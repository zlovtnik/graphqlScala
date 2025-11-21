package com.rcs.ssf.graphql;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GraphQL response for account status queries.
 * Maps AccountStatusDto to a GraphQL-friendly response format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusResponse {
    private Long userId;
    private String status; // ACTIVE, DEACTIVATED, SUSPENDED (as String from AccountStatus enum)
    private Long deactivatedAt;
    private String message; // Human-readable message about the status
}
