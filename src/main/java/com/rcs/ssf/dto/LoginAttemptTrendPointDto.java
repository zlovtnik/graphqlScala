package com.rcs.ssf.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginAttemptTrendPointDto {
    private LocalDate date;
    private long successCount;
    private long failedCount;
}