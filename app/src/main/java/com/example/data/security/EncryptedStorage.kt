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
import com.example.data.model.CustomSnippet
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
        private const val KEY_HERMES_PROVIDER = "hermes_provider"
        private const val KEY_LLM_PROVIDERS = "enc_llm_providers"

        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_PROFILE_IDS = "saved_profile_ids"
        private const val PREFIX_PROFILE = "profile_"

        private const val KEY_CUSTOM_SNIPPETS = "custom_terminal_snippets"
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
            android.util.Log.e("EncryptedStorage", "Enkripsi gagal pada AndroidKeyStore AES-GCM", e)
            throw IllegalStateException("Gagal mengenkripsi kredensial; data tidak disimpan", e)
        }
    }

    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) {
                return ""
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
            android.util.Log.e("EncryptedStorage", "Dekripsi gagal pada ciphertext", e)
            ""
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
            val rawName = prefs.getString("${PREFIX_PROFILE}${id}_name", null)
            if (rawName != null) {
                val name = rawName.removeEmojis().ifBlank { rawName }
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
                    hermesModel = if (prefs.contains("${PREFIX_PROFILE}${id}_hermes_model")) decrypt(encHermesModel) else "hermes-3-llama-3.1-8b",
                    hermesProvider = prefs.getString("${PREFIX_PROFILE}${id}_hermes_provider", "custom") ?: "custom",
                    llmProviders = decodeProviders(decrypt(prefs.getString("${PREFIX_PROFILE}${id}_llm_providers", "") ?: ""))
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
            .putString("${PREFIX_PROFILE}${profile.id}_hermes_provider", profile.config.hermesProvider)
            .putString("${PREFIX_PROFILE}${profile.id}_llm_providers", encrypt(encodeProviders(profile.config.withCurrentProviderSaved().llmProviders).toString()))
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
            .putString(KEY_HERMES_PROVIDER, config.hermesProvider)
            .putString(KEY_LLM_PROVIDERS, encrypt(encodeProviders(config.withCurrentProviderSaved().llmProviders).toString()))
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
            hermesModel = hermesModel,
            hermesProvider = prefs.getString(KEY_HERMES_PROVIDER, "custom") ?: "custom",
            llmProviders = decodeProviders(decrypt(prefs.getString(KEY_LLM_PROVIDERS, "") ?: ""))
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
                        put("isServerOutput", msg.isServerOutput)
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
                .putString("$PREFIX_HERMES_SESSION${session.id}", encrypt(jsonStr))
                .putString(KEY_HERMES_SESSION_IDS, currentIds.joinToString(","))
                .putString(KEY_ACTIVE_CHAT_SESSION_ID, session.id)
                .apply()
        } catch (_: Exception) {
            android.util.Log.e("EncryptedStorage", "Gagal menyimpan sesi Hermes")
        }
    }

    fun loadChatSession(sessionId: String): HermesChatSession? {
        val stored = prefs.getString("$PREFIX_HERMES_SESSION$sessionId", null) ?: return null
        return try {
            val raw = if (stored.trimStart().startsWith("{")) stored else decrypt(stored)
            if (raw.isBlank()) return null
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
                        actionType = actionType,
                        isServerOutput = m.optBoolean("isServerOutput", false)
                    )
                )
            }
            val session = HermesChatSession(
                id = id,
                title = title,
                createdAt = createdAt,
                updatedAt = updatedAt,
                messages = messages
            )
            if (stored.trimStart().startsWith("{")) saveChatSession(session)
            session
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

    // --- Custom Terminal Snippets Storage ---

    fun loadCustomSnippets(): List<CustomSnippet> {
        val stored = prefs.getString(KEY_CUSTOM_SNIPPETS, null)
        if (stored.isNullOrBlank()) {
            val defaults = CustomSnippet.getDefaultSnippets()
            saveAllSnippets(defaults)
            return defaults
        }
        return try {
            val raw = if (stored.trimStart().startsWith("[")) stored else decrypt(stored)
            if (raw.isBlank()) return CustomSnippet.getDefaultSnippets()
            val array = JSONArray(raw)
            val list = mutableListOf<CustomSnippet>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomSnippet(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Snippet"),
                        command = obj.optString("command", ""),
                        description = obj.optString("description", ""),
                        category = obj.optString("category", "Umum"),
                        isDefault = obj.optBoolean("isDefault", false)
                    )
                )
            }
            if (list.isEmpty()) {
                val defaults = CustomSnippet.getDefaultSnippets()
                saveAllSnippets(defaults)
                defaults
            } else {
                if (stored.trimStart().startsWith("[")) saveAllSnippets(list)
                list
            }
        } catch (e: Exception) {
            val defaults = CustomSnippet.getDefaultSnippets()
            saveAllSnippets(defaults)
            defaults
        }
    }

    fun saveAllSnippets(snippets: List<CustomSnippet>) {
        try {
            val array = JSONArray()
            for (snip in snippets) {
                val obj = JSONObject().apply {
                    put("id", snip.id)
                    put("name", snip.name)
                    put("command", snip.command)
                    put("description", snip.description)
                    put("category", snip.category)
                    put("isDefault", snip.isDefault)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_CUSTOM_SNIPPETS, encrypt(array.toString())).apply()
        } catch (_: Exception) {
            android.util.Log.e("EncryptedStorage", "Gagal menyimpan snippet terminal")
        }
    }

    fun addCustomSnippet(snippet: CustomSnippet) {
        val current = loadCustomSnippets().toMutableList()
        current.removeAll { it.id == snippet.id }
        current.add(snippet)
        saveAllSnippets(current)
    }

    fun deleteCustomSnippet(id: String) {
        val current = loadCustomSnippets().toMutableList()
        current.removeAll { it.id == id }
        saveAllSnippets(current)
    }

    fun resetDefaultSnippets(): List<CustomSnippet> {
        val defaults = CustomSnippet.getDefaultSnippets()
        saveAllSnippets(defaults)
        return defaults
    }

    // ==========================================
    // BACKUP & RESTORE CONNECTION PROFILES
    // ==========================================
    fun exportProfilesPayload(password: String): String {
        val profiles = loadProfiles()
        val root = JSONObject().apply {
            put("format", "HERMES_PROFILES_BACKUP")
            put("version", 1)
            put("exported_at", System.currentTimeMillis())
            val arr = JSONArray()
            for (p in profiles) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name.removeEmojis().trim().ifBlank { p.name })
                    put("sshHost", p.config.sshHost)
                    put("sshPort", p.config.sshPort)
                    put("sshUser", p.config.sshUser)
                    put("sshPassword", p.config.sshPassword)
                    put("hermesBaseUrl", p.config.hermesBaseUrl)
                    put("hermesApiKey", p.config.hermesApiKey)
                    put("hermesModel", p.config.hermesModel)
                    put("hermesProvider", p.config.hermesProvider)
                    put("llmProviders", encodeProviders(p.config.withCurrentProviderSaved().llmProviders))
                }
                arr.put(obj)
            }
            put("profiles", arr)
        }
        val jsonStr = root.toString(2)
        return BackupCipher.encrypt(jsonStr, password)
    }

    fun importProfilesPayload(rawInput: String, password: String = ""): Result<List<ConnectionProfile>> {
        return try {
            val cleanInput = rawInput.trim()
            val jsonStr = when {
                cleanInput.startsWith("HERMES_BACKUP_V2:") -> BackupCipher.decrypt(cleanInput, password)
                cleanInput.startsWith("HERMES_BACKUP_V1:") -> {
                    val b64 = cleanInput.removePrefix("HERMES_BACKUP_V1:").trim()
                    String(Base64.decode(b64, Base64.NO_WRAP), Charsets.UTF_8)
                }
                cleanInput.startsWith("{") -> {
                    cleanInput
                }
                else -> {
                    try {
                        String(Base64.decode(cleanInput, Base64.NO_WRAP), Charsets.UTF_8)
                    } catch (_: Exception) {
                        cleanInput
                    }
                }
            }

            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("profiles") ?: JSONArray()
            if (arr.length() == 0) {
                return Result.failure(IllegalArgumentException("Backup kosong atau tidak memiliki profil valid"))
            }

            val importedProfiles = mutableListOf<ConnectionProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", "profile_${System.currentTimeMillis()}_$i")
                val name = obj.optString("name", "Profil ${i + 1}").removeEmojis().trim().ifBlank { "Profil ${i + 1}" }
                val host = obj.optString("sshHost", "")
                val port = obj.optInt("sshPort", 22)
                val user = obj.optString("sshUser", "admin")
                val pass = obj.optString("sshPassword", "")
                val hermesUrl = obj.optString("hermesBaseUrl", "https://100.x.y.z:11434")
                val hermesKey = obj.optString("hermesApiKey", "")
                val hermesModel = obj.optString("hermesModel", "hermes-3-llama-3.1-8b")

                val config = ServerConfig(
                    sshHost = host,
                    sshPort = if (port in 1..65535) port else 22,
                    sshUser = user,
                    sshPassword = pass,
                    hermesBaseUrl = hermesUrl,
                    hermesApiKey = hermesKey,
                    hermesModel = hermesModel,
                    hermesProvider = obj.optString("hermesProvider", "custom"),
                    llmProviders = decodeProviders(obj.optJSONObject("llmProviders")?.toString().orEmpty())
                )
                importedProfiles.add(ConnectionProfile(id = id, name = name, config = config))
            }

            if (importedProfiles.isEmpty()) {
                return Result.failure(IllegalArgumentException("Format backup tidak valid: profil kosong"))
            }

            // Save to Encrypted Keystore
            for (p in importedProfiles) {
                saveProfile(p)
            }
            saveProfileIds(importedProfiles.map { it.id })
            setActiveProfileId(importedProfiles.first().id)
            saveConfig(importedProfiles.first().config)

            Result.success(importedProfiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun String.removeEmojis(): String {
    return this.replace(Regex("[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\p{Cs}\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u26FF\\u2700-\\u27BF]"), "").trim()
}
