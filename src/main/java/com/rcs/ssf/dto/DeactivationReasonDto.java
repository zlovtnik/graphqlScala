package com.rcs.ssf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for account deactivation reason input.
 * Contains a discrete reason code and optional free-text justification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeactivationReasonDto {
    
    @NotBlank(message = "Reason code is required")
    private String reasonCode;  // e.g., "USER_REQUESTED", "INACTIVITY", "ABUSE", "POLICY_VIOLATION"
    
    private String justification;  // Optional free-text explanation
}
