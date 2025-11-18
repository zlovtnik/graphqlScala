package com.rcs.ssf.security.mfa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DisabledBackupCodeServiceTest {

    private DisabledBackupCodeService service;

    @BeforeEach
    void setUp() {
        service = new DisabledBackupCodeService();
    }

    @Test
    void generateBackupCodesThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.generateBackupCodes("user"));
        assertDisabledMessage(ex);
    }

    @Test
    void verifyBackupCodeThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.verifyBackupCode("user", "code"));
        assertDisabledMessage(ex);
    }

    @Test
    void remainingBackupCodeCountThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.getRemainingBackupCodeCount("user"));
        assertDisabledMessage(ex);
    }

    @Test
    void regenerateBackupCodesThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.regenerateBackupCodes("user"));
        assertDisabledMessage(ex);
    }

    @Test
    void adminConsumeBackupCodeThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.adminConsumeBackupCode("user", "admin"));
        assertDisabledMessage(ex);
    }

    private void assertDisabledMessage(IllegalStateException ex) {
        assertEquals(
                "Backup code operations are disabled because the JDBC datasource is not enabled. " +
                        "Set app.datasource.enabled=true (and configure spring.datasource.*) to activate backup codes.",
                ex.getMessage());
    }
}
