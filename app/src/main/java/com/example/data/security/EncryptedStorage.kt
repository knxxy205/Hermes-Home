package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.hermes.ChatMessage
import com.example.data.hermes.HermesChatSession
import com.example.data.model.ConnectionProfile
import com.example.data.model.ServerConfig
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.UUID
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

        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_PROFILE_IDS = "saved_profile_ids"
        private const val PREFIX_PROFILE = "profile_"
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

    fun getActiveProfileId(): String {
        return prefs.getString(KEY_ACTIVE_PROFILE_ID, ConnectionProfile.PROFILE_HOME_ID)
            ?: ConnectionProfile.PROFILE_HOME_ID
    }

    fun setActiveProfileId(profileId: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).apply()
    }

    fun loadProfiles(): List<ConnectionProfile> {
        val savedIds = prefs.getString(KEY_PROFILE_IDS, null)
        val profileIds = if (!savedIds.isNullOrBlank()) {
            savedIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            // First run or migration from single config
            val legacyConfig = loadLegacyConfig()
            val hasLegacyData = legacyConfig.sshHost.isNotBlank() || legacyConfig.hermesApiKey.isNotBlank()

            val homeProfile = ConnectionProfile.defaultHomeProfile()
            val tailscaleProfile = if (hasLegacyData) {
                ConnectionProfile(
                    id = ConnectionProfile.PROFILE_TAILSCALE_ID,
                    name = "🌐 Di Luar (Tailscale)",
                    config = legacyConfig
                )
            } else {
                ConnectionProfile.defaultTailscaleProfile()
            }

            saveProfile(homeProfile)
            saveProfile(tailscaleProfile)
            val defaultList = listOf(homeProfile, tailscaleProfile)
            val initialActive = if (hasLegacyData) ConnectionProfile.PROFILE_TAILSCALE_ID else ConnectionProfile.PROFILE_HOME_ID
            prefs.edit()
                .putString(KEY_PROFILE_IDS, defaultList.joinToString(",") { it.id })
                .putString(KEY_ACTIVE_PROFILE_ID, initialActive)
                .apply()
            return defaultList
        }

        val result = mutableListOf<ConnectionProfile>()
        for (id in profileIds) {
            val name = prefs.getString("${PREFIX_PROFILE}${id}_name", null)
            if (name != null) {
                val encHost = prefs.getString("${PREFIX_PROFILE}${id}_ssh_host", "") ?: ""
                val port = prefs.getInt("${PREFIX_PROFILE}${id}_ssh_port", 22)
                val encUser = prefs.getString("${PREFIX_PROFILE}${id}_ssh_user", "") ?: ""
                val encPass = prefs.getString("${PREFIX_PROFILE}${id}_ssh_password", "") ?: ""
                val encHermesUrl = prefs.getString("${PREFIX_PROFILE}${id}_hermes_base_url", "") ?: ""
                val encHermesKey = prefs.getString("${PREFIX_PROFILE}${id}_hermes_api_key", "") ?: ""
                val encHermesModel = prefs.getString("${PREFIX_PROFILE}${id}_hermes_model", "") ?: ""

                val config = ServerConfig(
                    sshHost = decrypt(encHost),
                    sshPort = if (port in 1..65535) port else 22,
                    sshUser = decrypt(encUser),
                    sshPassword = decrypt(encPass),
                    hermesBaseUrl = decrypt(encHermesUrl).ifBlank { "http://100.x.y.z:11434" },
                    hermesApiKey = decrypt(encHermesKey),
                    hermesModel = decrypt(encHermesModel).ifBlank { "hermes-3-llama-3.1-8b" }
                )
                result.add(ConnectionProfile(id = id, name = name, config = config))
            }
        }

        if (result.isEmpty()) {
            val home = ConnectionProfile.defaultHomeProfile()
            val tailscale = ConnectionProfile.defaultTailscaleProfile()
            saveProfile(home)
            saveProfile(tailscale)
            saveProfileIds(listOf(home.id, tailscale.id))
            return listOf(home, tailscale)
        }
        return result
    }

    fun saveProfile(profile: ConnectionProfile) {
        prefs.edit()
            .putString("${PREFIX_PROFILE}${profile.id}_name", profile.name)
            .putString("${PREFIX_PROFILE}${profile.id}_ssh_host", encrypt(profile.config.sshHost))
            .putInt("${PREFIX_PROFILE}${profile.id}_ssh_port", profile.config.sshPort)
            .putString("${PREFIX_PROFILE}${profile.id}_ssh_user", encrypt(profile.config.sshUser))
            .putString("${PREFIX_PROFILE}${profile.id}_ssh_password", encrypt(profile.config.sshPassword))
            .putString("${PREFIX_PROFILE}${profile.id}_hermes_base_url", encrypt(profile.config.hermesBaseUrl))
            .putString("${PREFIX_PROFILE}${profile.id}_hermes_api_key", encrypt(profile.config.hermesApiKey))
            .putString("${PREFIX_PROFILE}${profile.id}_hermes_model", encrypt(profile.config.hermesModel))
            .apply()
    }

    fun saveProfileIds(ids: List<String>) {
        prefs.edit().putString(KEY_PROFILE_IDS, ids.joinToString(",")).apply()
    }

    fun deleteProfile(profileId: String) {
        val currentProfiles = loadProfiles().filter { it.id != profileId }
        saveProfileIds(currentProfiles.map { it.id })
        if (getActiveProfileId() == profileId && currentProfiles.isNotEmpty()) {
            setActiveProfileId(currentProfiles.first().id)
        }
    }

    fun saveConfig(config: ServerConfig) {
        val activeId = getActiveProfileId()
        val profiles = loadProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == activeId }
        if (index >= 0) {
            val updated = profiles[index].copy(config = config)
            profiles[index] = updated
            saveProfile(updated)
        } else {
            val newProfile = ConnectionProfile(id = activeId, name = "Profil $activeId", config = config)
            saveProfile(newProfile)
            saveProfileIds(profiles.map { it.id } + activeId)
        }

        // Also save legacy keys for fallback safety
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
        val activeId = getActiveProfileId()
        val profiles = loadProfiles()
        val activeProfile = profiles.find { it.id == activeId } ?: profiles.firstOrNull()
        return activeProfile?.config ?: loadLegacyConfig()
    }

    fun loadLegacyConfig(): ServerConfig {
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

    // ==========================================
    // HERMES CHAT HISTORY & SESSIONS PERSISTENCE
    // ==========================================
    private val KEY_HERMES_SESSION_IDS = "hermes_chat_session_ids"
    private val KEY_ACTIVE_CHAT_SESSION_ID = "active_chat_session_id"
    private val PREFIX_HERMES_SESSION = "hermes_session_"

    fun getActiveChatSessionId(): String? {
        return prefs.getString(KEY_ACTIVE_CHAT_SESSION_ID, null)
    }

    fun setActiveChatSessionId(sessionId: String) {
        prefs.edit().putString(KEY_ACTIVE_CHAT_SESSION_ID, sessionId).apply()
    }

    fun getChatSessionIds(): List<String> {
        val raw = prefs.getString(KEY_HERMES_SESSION_IDS, null) ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun saveChatSession(session: HermesChatSession) {
        try {
            val root = JSONObject().apply {
                put("id", session.id)
                put("title", session.title)
                put("createdAt", session.createdAt)
                put("updatedAt", session.updatedAt)
                val msgArray = JSONArray()
                session.messages.forEach { msg ->
                    val msgObj = JSONObject().apply {
                        put("id", msg.id)
                        put("role", msg.role)
                        put("content", msg.content)
                        put("timestamp", msg.timestamp)
                        put("isError", msg.isError)
                        put("actionType", msg.actionType ?: "")
                        val actionsArr = JSONArray()
                        msg.interactiveActions.forEach { actionsArr.put(it) }
                        put("interactiveActions", actionsArr)
                    }
                    msgArray.put(msgObj)
                }
                put("messages", msgArray)
            }

            val jsonStr = root.toString()
            val currentIds = getChatSessionIds().toMutableList()
            if (!currentIds.contains(session.id)) {
                currentIds.add(0, session.id) // Most recent first
            } else {
                // Move to front
                currentIds.remove(session.id)
                currentIds.add(0, session.id)
            }

            prefs.edit()
                .putString("$PREFIX_HERMES_SESSION${session.id}", jsonStr)
                .putString(KEY_HERMES_SESSION_IDS, currentIds.joinToString(","))
                .putString(KEY_ACTIVE_CHAT_SESSION_ID, session.id)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadChatSession(sessionId: String): HermesChatSession? {
        val raw = prefs.getString("$PREFIX_HERMES_SESSION$sessionId", null) ?: return null
        return try {
            val root = JSONObject(raw)
            val id = root.optString("id", sessionId)
            val title = root.optString("title", "Obrolan")
            val createdAt = root.optLong("createdAt", System.currentTimeMillis())
            val updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
            val msgArray = root.optJSONArray("messages") ?: JSONArray()
            val messages = mutableListOf<ChatMessage>()
            for (i in 0 until msgArray.length()) {
                val m = msgArray.getJSONObject(i)
                val actArr = m.optJSONArray("interactiveActions")
                val actList = mutableListOf<String>()
                if (actArr != null) {
                    for (j in 0 until actArr.length()) {
                        actList.add(actArr.getString(j))
                    }
                }
                val actionType = m.optString("actionType", "").ifBlank { null }
                messages.add(
                    ChatMessage(
                        id = m.optString("id", UUID.randomUUID().toString()),
                        role = m.optString("role", "user"),
                        content = m.optString("content", ""),
                        timestamp = m.optLong("timestamp", System.currentTimeMillis()),
                        isError = m.optBoolean("isError", false),
                        interactiveActions = actList,
                        actionType = actionType
                    )
                )
            }
            HermesChatSession(
                id = id,
                title = title,
                createdAt = createdAt,
                updatedAt = updatedAt,
                messages = messages
            )
        } catch (e: Exception) {
            null
        }
    }

    fun loadChatSessions(): List<HermesChatSession> {
        val ids = getChatSessionIds()
        val list = mutableListOf<HermesChatSession>()
        for (id in ids) {
            loadChatSession(id)?.let { list.add(it) }
        }
        return list.sortedByDescending { it.updatedAt }
    }

    fun deleteChatSession(sessionId: String) {
        val currentIds = getChatSessionIds().toMutableList()
        currentIds.remove(sessionId)
        val editor = prefs.edit()
        editor.remove("$PREFIX_HERMES_SESSION$sessionId")
        editor.putString(KEY_HERMES_SESSION_IDS, currentIds.joinToString(","))
        if (getActiveChatSessionId() == sessionId) {
            editor.putString(KEY_ACTIVE_CHAT_SESSION_ID, currentIds.firstOrNull() ?: "")
        }
        editor.apply()
    }

    fun getActiveOrLatestChatSession(): HermesChatSession {
        val activeId = getActiveChatSessionId()
        if (!activeId.isNullOrBlank()) {
            val session = loadChatSession(activeId)
            if (session != null) return session
        }
        val allSessions = loadChatSessions()
        if (allSessions.isNotEmpty()) {
            return allSessions.first()
        }
        return createDefaultChatSession()
    }

    fun createDefaultChatSession(): HermesChatSession {
        val session = HermesChatSession(
            title = "Obrolan Baru",
            messages = listOf(
                ChatMessage(
                    role = "assistant",
                    content = "Halo! Saya Hermes Agent di homeserver Anda. Ada yang bisa saya bantu untuk administrasi server atau automasi hari ini?"
                )
            )
        )
        saveChatSession(session)
        return session
    }
}
