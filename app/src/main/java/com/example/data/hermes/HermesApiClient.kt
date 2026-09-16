package com.example.data.hermes

import com.example.data.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant" or "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

class HermesApiClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun normalizeEndpoint(baseUrl: String): String {
        var cleanUrl = baseUrl.trim().trimEnd('/')
        if (cleanUrl.endsWith("/v1/chat/completions")) {
            return cleanUrl
        }
        if (cleanUrl.endsWith("/v1")) {
            return "$cleanUrl/chat/completions"
        }
        return "$cleanUrl/v1/chat/completions"
    }

    suspend fun sendMessage(
        userMessage: String,
        history: List<ChatMessage>,
        config: ServerConfig,
        useSandboxFallback: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isHermesConfigured) {
            return@withContext Result.failure(
                IllegalArgumentException("Hermes URL / Model belum diatur. Atur di tab Settings.")
            )
        }

        val endpoint = normalizeEndpoint(config.hermesBaseUrl)

        // Build messages array
        val messagesJsonArray = JSONArray()

        // System prompt context
        val systemPrompt = JSONObject().apply {
            put("role", "system")
            put("content", "You are Hermes, an expert AI assistant specialized in homeserver management, Linux CLI, Docker containers, networking, and server administration.")
        }
        messagesJsonArray.put(systemPrompt)

        // Previous context (up to last 10 messages)
        val contextHistory = history.takeLast(10)
        for (msg in contextHistory) {
            val obj = JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            }
            messagesJsonArray.put(obj)
        }

        // New user message
        val currentMsgObj = JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        }
        messagesJsonArray.put(currentMsgObj)

        val requestBodyJson = JSONObject().apply {
            put("model", config.hermesModel)
            put("messages", messagesJsonArray)
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))

        if (config.hermesApiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${config.hermesApiKey}")
        }

        try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("HTTP ${response.code}: $responseBody")
                )
            }

            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val messageObj = firstChoice.getJSONObject("message")
                val content = messageObj.getString("content")
                Result.success(content)
            } else {
                Result.failure(Exception("Empty choices array from Hermes response"))
            }
        } catch (e: Exception) {
            if (useSandboxFallback) {
                // Helpful sandbox response for testing without live server
                val mockReply = generateSandboxReply(userMessage)
                Result.success(mockReply)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchAvailableModels(baseUrl: String, apiKey: String = ""): Result<List<String>> =
        fetchAvailableModels(ServerConfig(hermesBaseUrl = baseUrl, hermesApiKey = apiKey))

    suspend fun fetchAvailableModels(config: ServerConfig): Result<List<String>> = withContext(Dispatchers.IO) {
        if (config.hermesBaseUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Base URL belum diisi"))
        }

        var cleanUrl = config.hermesBaseUrl.trim().trimEnd('/')
        if (cleanUrl.endsWith("/v1/chat/completions")) {
            cleanUrl = cleanUrl.removeSuffix("/v1/chat/completions")
        } else if (cleanUrl.endsWith("/v1")) {
            cleanUrl = cleanUrl.removeSuffix("/v1")
        }

        val modelsList = mutableListOf<String>()

        // 1. Try OpenAI-compatible GET /v1/models
        try {
            val reqBuilder = Request.Builder()
                .url("$cleanUrl/v1/models")
                .get()
            if (config.hermesApiKey.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer ${config.hermesApiKey}")
            }
            val resp = httpClient.newCall(reqBuilder.build()).execute()
            if (resp.isSuccessful) {
                val jsonStr = resp.body?.string() ?: ""
                val json = JSONObject(jsonStr)
                if (json.has("data")) {
                    val dataArr = json.getJSONArray("data")
                    for (i in 0 until dataArr.length()) {
                        val item = dataArr.getJSONObject(i)
                        val id = item.optString("id")
                        if (id.isNotBlank()) modelsList.add(id)
                    }
                }
            }
        } catch (_: Exception) {}

        if (modelsList.isNotEmpty()) {
            return@withContext Result.success(modelsList)
        }

        // 2. Try Ollama native GET /api/tags
        try {
            val req = Request.Builder()
                .url("$cleanUrl/api/tags")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val jsonStr = resp.body?.string() ?: ""
                val json = JSONObject(jsonStr)
                if (json.has("models")) {
                    val modelsArr = json.getJSONArray("models")
                    for (i in 0 until modelsArr.length()) {
                        val item = modelsArr.getJSONObject(i)
                        val name = item.optString("name")
                        if (name.isNotBlank()) modelsList.add(name)
                    }
                }
            }
        } catch (_: Exception) {}

        if (modelsList.isNotEmpty()) {
            Result.success(modelsList)
        } else {
            Result.failure(Exception("Tidak dapat menemukan daftar model di $cleanUrl/v1/models atau /api/tags. Pastikan host dan port benar."))
        }
    }

    suspend fun testConnection(config: ServerConfig): Result<String> = withContext(Dispatchers.IO) {
        val endpoint = normalizeEndpoint(config.hermesBaseUrl)
        val bodyJson = JSONObject().apply {
            put("model", config.hermesModel)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "ping")
            }))
            put("max_tokens", 10)
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toString().toRequestBody(jsonMediaType))

        if (config.hermesApiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${config.hermesApiKey}")
        }

        try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                Result.success("Koneksi Hermes AI Berhasil! (HTTP ${response.code})")
            } else {
                val msg = response.body?.string() ?: response.message
                Result.failure(Exception("HTTP ${response.code}: $msg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateSandboxReply(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("docker") || lower.contains("container") ->
                "Untuk memeriksa container homeserver, Anda bisa menjalankan:\n```bash\ndocker ps -a\n```\nContainer Anda yang sedang berjalan:\n- `hermes-core` (port 11434)\n- `caddy-proxy` (port 80, 443)\n- `portainer` (port 9000)\n\nSemua berjalan normal dengan resource load rendah."

            lower.contains("backup") || lower.contains("rsync") ->
                "Berikut contoh script rsync otomatis untuk backup homeserver:\n```bash\n#!/bin/bash\nSOURCE=\"/home/admin/data/\"\nDEST=\"/mnt/storage/backups/$(date +%F)/\"\nmkdir -p \"\$DEST\"\nrsync -avh --progress --delete \"\$SOURCE\" \"\$DEST\"\n```\nJadwalkan via cron dengan `crontab -e` pada jam 02:00 pagi setiap hari."

            lower.contains("disk") || lower.contains("storage") || lower.contains("df") ->
                "Status disk homeserver Anda saat ini:\n- NVMe Root (`/`): 21% terpakai (727 GB tersedia)\n- Storage HDD (`/mnt/storage`): 35% terpakai (2.4 TB tersedia)\n\nKondisi storage sangat sehat!"

            else ->
                "Halo! Saya Hermes Agent di homeserver Anda. Saya dapat membantu Anda memantau status server, membuat script bash/cron, mengelola docker container, atau mengecek log sistem. Silakan berikan perintah atau pertanyaan."
        }
    }
}
