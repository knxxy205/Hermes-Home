package com.example.data.hermes

import com.example.data.model.ServerConfig
import com.example.data.model.LlmProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    val isError: Boolean = false,
    val interactiveActions: List<String> = emptyList(),
    val actionType: String? = null,
    val isServerOutput: Boolean = false
) {
    val isExecutionResult: Boolean
        get() {
            if (isServerOutput) return true
            val heading = content.trimStart().removePrefix("⚡").removePrefix("❌").trimStart()
            return role == "assistant" && (heading.startsWith("**Hasil Eksekusi di Server:") ||
                heading.startsWith("**Gagal mengeksekusi di server:"))
        }

    val canOfferCommandActions: Boolean
        get() = role == "assistant" && !isError && !isExecutionResult
}

data class HermesChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Obrolan Baru",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
) {
    fun previewText(): String {
        val lastMsg = messages.lastOrNull { it.role != "system" }
        return lastMsg?.content?.take(60) ?: "Belum ada pesan"
    }
}

class HermesApiClient(baseClient: OkHttpClient = OkHttpClient()) {

    private val httpClient = baseClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(HermesEndpointPolicy.interceptor)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun validateEndpoint(rawUrl: String): Result<Unit> = HermesEndpointPolicy.validate(rawUrl)

    private fun apiBase(baseUrl: String): String {
        val url = baseUrl.trim().toHttpUrl()
        require(url.query == null && url.fragment == null) { "Base URL tidak boleh berisi query atau fragment. Isi API key pada kolom API key." }
        val clean = url.toString().trimEnd('/').removeSuffix("/chat/completions")
        return if (url.encodedPath == "/") "$clean/v1" else clean
    }

    private fun normalizeEndpoint(config: ServerConfig) = "${apiBase(config.hermesBaseUrl)}/${if (config.hermesProvider == "claude") "messages" else "chat/completions"}"

    private fun Request.Builder.withProviderAuth(config: ServerConfig): Request.Builder = apply {
        if (config.hermesProvider == "claude") {
            header("x-api-key", config.hermesApiKey)
            header("anthropic-version", "2023-06-01")
        } else if (config.hermesApiKey.isNotBlank()) header("Authorization", "Bearer ${config.hermesApiKey}")
    }

    private fun responseText(body: String, config: ServerConfig): String {
        val root = JSONObject(body)
        if (config.hermesProvider == "claude") {
            val blocks = root.getJSONArray("content")
            return (0 until blocks.length()).mapNotNull { index -> blocks.optJSONObject(index)?.let { block ->
                block.optString("text").takeIf { block.optString("type") == "text" }
            } }.joinToString("\n")
        }
        return root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            .optString("content").takeUnless { it == "null" }.orEmpty()
    }

    private fun requestError(code: Int, body: String, key: String): Exception {
        val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        val message = when (code) {
            401, 403 -> "API key ditolak atau tidak memiliki akses ke model ini. Periksa key dan izin provider."
            402 -> "Saldo provider tidak cukup. Periksa kredit akun."
            404 -> "Model atau endpoint tidak ditemukan. Pilih model dari daftar provider."
            429 -> "Batas permintaan atau kuota provider tercapai. Coba lagi setelah jeda atau periksa kuota."
            in 500..599 -> "Provider sedang bermasalah. Coba lagi nanti."
            else -> detail.ifBlank { "Permintaan ditolak oleh provider." }
        }
        return Exception("HTTP $code: " + (if (key.isNotEmpty()) message.replace(key, "[disembunyikan]") else message).take(500))
    }

    suspend fun sendMessage(
        userMessage: String,
        history: List<ChatMessage>,
        config: ServerConfig,
        useSandboxFallback: Boolean = false,
        responseInstructions: String = "",
        reasoningEffort: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isHermesConfigured) {
            return@withContext Result.failure(
                IllegalArgumentException("Isi Base URL, API key provider, dan model di Settings. Server custom dapat memakai key kosong.")
            )
        }

        val endpoint = runCatching { normalizeEndpoint(config) }.getOrElse { return@withContext Result.failure(it) }
        validateEndpoint(endpoint).getOrElse { return@withContext Result.failure(it) }

        // Build messages array
        val messagesJsonArray = JSONArray()

        val sshContext = if (config.isSshConfigured) "Configured SSH target ${config.sshUser}@${config.sshHost}:${config.sshPort}" else "SSH target not configured"
        val systemPrompt = JSONObject().apply {
            put("role", "system")
            put("content", "You are Hermes, a homeserver assistant in Hermes Home. Server context: $sshContext. " +
                "Reply in the user's language, using clear paragraphs, short headings, and lists where useful. " +
                "Put shell commands in fenced bash blocks and explain their purpose. Commands run only after the user confirms an action in the app. " +
                "The app already executes commands on the configured SSH target. Do not wrap commands in another ssh invocation unless the user explicitly requests a different host. " +
                "Use actual command output supplied in the conversation to assess server state. State uncertainty when information is missing. " +
                "Give the next useful action and a way to verify the result.\n" + responseInstructions)
        }
        if (config.hermesProvider != "claude") messagesJsonArray.put(systemPrompt)

        // Previous context (up to last 10 messages)
        val eligibleHistory = history.filter { !it.isError && it.role in listOf("user", "assistant") }.takeLast(10)
        val contextHistory = if (config.hermesProvider == "claude") eligibleHistory.dropWhile { it.role != "user" } else eligibleHistory
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
            val supportedEffort = reasoningEffort?.lowercase()?.takeIf { it in setOf("low", "medium", "high") }
                ?.takeIf { supportsReasoningEffort(config.hermesProvider, config.hermesModel) }
            when {
                supportedEffort == null -> Unit
                config.hermesProvider == "claude" -> put("output_config", JSONObject().put("effort", supportedEffort))
                config.hermesProvider == "openrouter" -> put("reasoning", JSONObject().put("effort", supportedEffort))
                else -> put("reasoning_effort", supportedEffort)
            }
            if (config.hermesProvider == "claude") {
                put("system", systemPrompt.getString("content"))
                put("max_tokens", 4096)
            }
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))

        requestBuilder.withProviderAuth(config)

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        requestError(response.code, responseBody, config.hermesApiKey)
                    )
                }

                val content = responseText(responseBody, config)
                if (content.isNotBlank()) Result.success(content)
                else Result.failure(Exception("Model tidak mengembalikan teks. Pilih model chat yang mendukung teks."))
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

    private fun supportsReasoningEffort(provider: String, model: String): Boolean {
        val id = model.trim().lowercase()
        val modelId = if (provider == "openrouter") id.substringAfter('/', "") else id
        val openAiReasoningModel = Regex("^(gpt-[56](?:[.-].*)?|o[134](?:[.-].*)?)$").matches(modelId)
        val geminiReasoningModel = Regex("^gemini-(?:2\\.5|3|4)(?:[-.]|$).*").matches(modelId)
        val claudeEffortModel = Regex("^claude-(?:opus|sonnet|haiku)-(?:4-(?:5|6|7|8)|5)(?:[-.]|$).*").matches(modelId)

        return when (provider) {
            "openai" -> openAiReasoningModel
            "google" -> geminiReasoningModel
            "claude" -> claudeEffortModel
            "openrouter" -> when {
                id.startsWith("openai/") -> openAiReasoningModel
                id.startsWith("google/") -> geminiReasoningModel
                id.startsWith("anthropic/") -> claudeEffortModel
                else -> false
            }
            else -> false
        }
    }

    private fun extractModelNames(jsonStr: String): List<String> {
        val names = mutableListOf<String>()
        val trimmed = jsonStr.trim()
        try {
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val item = array.opt(i)
                    when (item) {
                        is String -> if (item.isNotBlank()) names.add(item)
                        is JSONObject -> {
                            val id = item.optString("id").ifBlank {
                                item.optString("name").ifBlank { item.optString("model") }
                            }
                            if (id.isNotBlank()) names.add(id)
                        }
                    }
                }
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                val candidateArrays = listOf("data", "models", "items", "list")
                for (key in candidateArrays) {
                    if (obj.has(key)) {
                        val arr = obj.optJSONArray(key) ?: continue
                        for (i in 0 until arr.length()) {
                            val item = arr.opt(i)
                            when (item) {
                                is String -> if (item.isNotBlank()) names.add(item)
                                is JSONObject -> {
                                    val id = item.optString("id").ifBlank {
                                        item.optString("name").ifBlank {
                                            item.optString("model").ifBlank {
                                                item.optString("model_name")
                                            }
                                        }
                                    }
                                    if (id.isNotBlank()) names.add(id)
                                }
                            }
                        }
                        if (names.isNotEmpty()) break
                    }
                }
            }
        } catch (_: Exception) {}
        return names.distinct()
    }

    suspend fun fetchAvailableModels(baseUrl: String, apiKey: String = ""): Result<List<String>> =
        fetchAvailableModels(ServerConfig(hermesBaseUrl = baseUrl, hermesApiKey = apiKey))

    suspend fun fetchAvailableModels(config: ServerConfig): Result<List<String>> = withContext(Dispatchers.IO) {
        if (config.hermesBaseUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Base URL belum diisi"))
        }

        if (LlmProvider.fromId(config.hermesProvider).requiresKey && config.hermesApiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Isi API key ${LlmProvider.fromId(config.hermesProvider).label} terlebih dahulu."))
        }
        val base = runCatching { apiBase(config.hermesBaseUrl) }.getOrElse { return@withContext Result.failure(it) }
        val cleanUrl = base.removeSuffix("/v1")
        validateEndpoint(cleanUrl).getOrElse { return@withContext Result.failure(it) }

        // Candidate endpoints covering 9router, OpenAI, Ollama, vLLM, LiteLLM, LMDeploy
        val candidateEndpoints = if (config.hermesProvider != "custom") listOf("$base/models") else listOf(
            "$base/models",
            "$cleanUrl/models",
            "$cleanUrl/api/v1/models",
            "$cleanUrl/api/tags",
            "$cleanUrl/api/models",
            "$cleanUrl/api/ps"
        ).distinct()

        val discovered = mutableListOf<String>()
        var emptyCatalog = false

        for (endpoint in candidateEndpoints) {
            try {
                val reqBuilder = Request.Builder()
                    .url(endpoint)
                    .get()
                reqBuilder.withProviderAuth(config)
                val models = httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    when (response.code) {
                        401, 402, 403, 429 -> return@withContext Result.failure(requestError(response.code, response.body?.string().orEmpty(), config.hermesApiKey))
                    }
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val names = extractModelNames(body)
                        val catalog = runCatching {
                            if (body.trimStart().startsWith("[")) { JSONArray(body); true }
                            else { val root = JSONObject(body); listOf("data", "models", "items", "list").any { root.opt(it) is JSONArray } }
                        }.getOrDefault(false)
                        if (catalog && names.isEmpty()) emptyCatalog = true
                        names
                    } else emptyList()
                }
                if (models.isNotEmpty()) {
                    discovered.addAll(models)
                    break
                }
            } catch (_: Exception) {
                // Continue to next candidate endpoint
            }
        }

        val uniqueModels = discovered.distinct()
        if (uniqueModels.isNotEmpty() || emptyCatalog) {
            Result.success(uniqueModels)
        } else {
            Result.failure(Exception("Daftar model belum tersedia. Periksa Base URL, koneksi, dan akses API key; atau isi nama model secara manual."))
        }
    }

    suspend fun testConnection(config: ServerConfig): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isHermesConfigured) return@withContext Result.failure(IllegalArgumentException("Isi Base URL, model, dan API key provider terlebih dahulu."))
        val endpoint = runCatching { normalizeEndpoint(config) }.getOrElse { return@withContext Result.failure(it) }
        validateEndpoint(endpoint).getOrElse { return@withContext Result.failure(it) }
        val bodyJson = JSONObject().apply {
            put("model", config.hermesModel)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "Reply with OK.")
            }))
            if (config.hermesProvider == "claude") put("max_tokens", 64)
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toString().toRequestBody(jsonMediaType))

        requestBuilder.withProviderAuth(config)

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val text = runCatching { responseText(response.body?.string().orEmpty(), config) }.getOrDefault("")
                    if (text.isBlank() || text == "null") Result.failure(Exception("Endpoint terhubung tetapi tidak mengembalikan jawaban chat. Periksa model."))
                    else Result.success("Model ${config.hermesModel} berhasil menjawab (HTTP ${response.code}).")
                } else {
                    val msg = response.body?.string() ?: response.message
                    Result.failure(requestError(response.code, msg, config.hermesApiKey))
                }
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
