package com.example.data.security

import com.example.data.model.LlmProvider
import com.example.data.model.LlmProviderSettings
import org.json.JSONObject

internal fun encodeProviders(providers: Map<String, LlmProviderSettings>): JSONObject = JSONObject().apply {
    providers.forEach { (id, value) ->
        put(id, JSONObject().apply {
            put("baseUrl", value.baseUrl)
            put("apiKey", value.apiKey)
            put("model", value.model)
        })
    }
}

internal fun decodeProviders(raw: String): Map<String, LlmProviderSettings> {
    if (raw.isBlank()) return emptyMap()
    return runCatching {
        val root = JSONObject(raw)
        LlmProvider.entries.mapNotNull { provider ->
            root.optJSONObject(provider.id)?.let { value ->
                provider.id to LlmProviderSettings(value.optString("baseUrl"), value.optString("apiKey"), value.optString("model"))
            }
        }.toMap()
    }.getOrDefault(emptyMap())
}
