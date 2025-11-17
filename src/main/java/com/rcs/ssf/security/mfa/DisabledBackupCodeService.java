package com.rcs.ssf.security.mfa;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Fallback {@link BackupCodeService} that activates when the blocking JDBC
 * datasource is disabled. Each entry point throws an informative
 * {@link IllegalStateException} so callers receive fast feedback that backup
 * codes require {@code app.datasource.enabled=true} plus a configured
 * {@code spring.datasource.*} block.
 */
@Service
@Slf4j
@ConditionalOnMissingBean(BackupCodeService.class)
@ConditionalOnProperty(prefix = "app.datasource", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledBackupCodeService implements BackupCodeService {

    private static final String ERROR_MESSAGE = "Backup code operations are disabled because the JDBC datasource is not enabled. "
            +
            "Set app.datasource.enabled=true (and configure spring.datasource.*) to activate backup codes.";

    @Override
    public List<String> generateBackupCodes(String userId) {
        throw unsupported("generateBackupCodes");
    }

    @Override
    public boolean verifyBackupCode(String userId, String code) {
        throw unsupported("verifyBackupCode");
    }

    @Override
    public int getRemainingBackupCodeCount(String userId) {
        throw unsupported("getRemainingBackupCodeCount");
    }

    @Override
    public List<String> regenerateBackupCodes(String userId) {
        throw unsupported("regenerateBackupCodes");
    }

    @Override
    public boolean adminConsumeBackupCode(String userId, String adminId) {
        throw unsupported("adminConsumeBackupCode");
    }

    private IllegalStateException unsupported(String operation) {
        log.warn("BackupCodeService operation '{}' invoked while JDBC datasource is disabled", operation);
        return new IllegalStateException(ERROR_MESSAGE);
    }
}
