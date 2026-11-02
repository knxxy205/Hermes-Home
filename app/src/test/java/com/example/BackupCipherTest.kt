package com.example

import com.example.data.security.BackupCipher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupCipherTest {
    @Test fun backupRoundTripAndWrongPassword() {
        val payload = BackupCipher.encrypt("private server settings", "correct-password-123")
        assertTrue(payload.startsWith("HERMES_BACKUP_V2:"))
        assertEquals("private server settings", BackupCipher.decrypt(payload, "correct-password-123"))
        assertTrue(runCatching { BackupCipher.decrypt(payload, "different-password-123") }.isFailure)
    }

    @Test fun encryptionUsesFreshSaltAndIv() {
        assertNotEquals(BackupCipher.encrypt("same", "correct-password-123"), BackupCipher.encrypt("same", "correct-password-123"))
    }
}
