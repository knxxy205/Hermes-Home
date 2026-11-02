package com.example.data.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/** Portable password-protected backups, independent of the device Keystore. */
object BackupCipher {
    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        require(password.length >= 12) { "Password backup minimal 12 karakter" }
        val spec = PBEKeySpec(password.toCharArray(), salt, 210_000, 256)
        return try {
            // HMAC-SHA1 PBKDF2 is available on every supported Android API (24+).
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }

    fun encrypt(text: String, password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt))
        val data = salt + cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return "HERMES_BACKUP_V2:" + Base64.encodeToString(data, Base64.NO_WRAP)
    }

    fun decrypt(payload: String, password: String): String {
        val data = Base64.decode(payload.removePrefix("HERMES_BACKUP_V2:"), Base64.NO_WRAP)
        require(data.size >= 44) { "Backup tidak valid" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, data.copyOfRange(0, 16)), GCMParameterSpec(128, data.copyOfRange(16, 28)))
        return String(cipher.doFinal(data.copyOfRange(28, data.size)), Charsets.UTF_8)
    }
}
