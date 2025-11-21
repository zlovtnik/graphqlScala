package com.rcs.ssf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for account deactivation/reactivation flows.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusDto {
    private Long userId;
    
    @NotBlank(message = "Account status must be provided")
    private String status; // ACTIVE, DEACTIVATED, SUSPENDED

    private Long deactivatedAt;
    private String reason; // Optional reason for deactivation
}
