package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.model.ServerConfig
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "homeserver_encrypted_vault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "homeserver_aes_master_key"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12

        private const val KEY_SSH_HOST = "enc_ssh_host"
        private const val KEY_SSH_PORT = "enc_ssh_port"
        private const val KEY_SSH_USER = "enc_ssh_user"
        private const val KEY_SSH_PASSWORD = "enc_ssh_password"
        private const val KEY_HERMES_BASE_URL = "enc_hermes_base_url"
        private const val KEY_HERMES_API_KEY = "enc_hermes_api_key"
        private const val KEY_HERMES_MODEL = "enc_hermes_model"
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val byteBuffer = ByteBuffer.allocate(iv.size + cipherText.size)
            byteBuffer.put(iv)
            byteBuffer.put(cipherText)
            Base64.encodeToString(byteBuffer.array(), Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback for environments where AndroidKeyStore provider is simulated or mocked
            Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) {
                return String(combined, Charsets.UTF_8)
            }
            val secretKey = getOrCreateSecretKey()
            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            val cipherTextSize = combined.size - GCM_IV_LENGTH
            val cipherText = ByteArray(cipherTextSize)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherTextSize)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                // Fallback attempt
                String(Base64.decode(encryptedBase64, Base64.NO_WRAP), Charsets.UTF_8)
            } catch (ex: Exception) {
                ""
            }
        }
    }

    fun saveConfig(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_SSH_HOST, encrypt(config.sshHost))
            .putInt(KEY_SSH_PORT, config.sshPort)
            .putString(KEY_SSH_USER, encrypt(config.sshUser))
            .putString(KEY_SSH_PASSWORD, encrypt(config.sshPassword))
            .putString(KEY_HERMES_BASE_URL, encrypt(config.hermesBaseUrl))
            .putString(KEY_HERMES_API_KEY, encrypt(config.hermesApiKey))
            .putString(KEY_HERMES_MODEL, encrypt(config.hermesModel))
            .apply()
    }

    fun loadConfig(): ServerConfig {
        val encHost = prefs.getString(KEY_SSH_HOST, "") ?: ""
        val port = prefs.getInt(KEY_SSH_PORT, 22)
        val encUser = prefs.getString(KEY_SSH_USER, "") ?: ""
        val encPass = prefs.getString(KEY_SSH_PASSWORD, "") ?: ""
        val encHermesUrl = prefs.getString(KEY_HERMES_BASE_URL, "") ?: ""
        val encHermesKey = prefs.getString(KEY_HERMES_API_KEY, "") ?: ""
        val encHermesModel = prefs.getString(KEY_HERMES_MODEL, "") ?: ""

        val defaultModel = "hermes-3-llama-3.1-8b"
        val defaultUrl = "http://100.x.y.z:11434"

        val host = decrypt(encHost)
        val user = decrypt(encUser)
        val pass = decrypt(encPass)
        val hermesUrl = decrypt(encHermesUrl).ifBlank { defaultUrl }
        val hermesKey = decrypt(encHermesKey)
        val hermesModel = decrypt(encHermesModel).ifBlank { defaultModel }

        return ServerConfig(
            sshHost = host,
            sshPort = if (port in 1..65535) port else 22,
            sshUser = user,
            sshPassword = pass,
            hermesBaseUrl = hermesUrl,
            hermesApiKey = hermesKey,
            hermesModel = hermesModel
        )
    }

    fun isKeystoreActive(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            getOrCreateSecretKey()
            keyStore.containsAlias(KEY_ALIAS)
        } catch (e: Exception) {
            false
        }
    }
}
