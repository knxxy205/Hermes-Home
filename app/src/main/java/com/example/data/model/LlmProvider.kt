package com.example.data.model

enum class LlmProvider(val id: String, val label: String, val baseUrl: String) {
    CUSTOM("custom", "9router / server custom", ""),
    GOOGLE("google", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1"),
    CLAUDE("claude", "Claude (Anthropic)", "https://api.anthropic.com/v1"),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1");

    val requiresKey: Boolean get() = this != CUSTOM

    companion object {
        fun fromId(id: String): LlmProvider = entries.firstOrNull { it.id == id } ?: CUSTOM
    }
}

data class LlmProviderSettings(val baseUrl: String = "", val apiKey: String = "", val model: String = "")
