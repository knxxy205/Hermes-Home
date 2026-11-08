package com.example.data.model

data class ServerConfig(
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUser: String = "",
    val sshPassword: String = "",
    val hermesBaseUrl: String = "http://100.100.100.100:11434",
    val hermesApiKey: String = "",
    val hermesModel: String = "hermes-3-llama-3.1-8b",
    val hermesProvider: String = "custom",
    val llmProviders: Map<String, LlmProviderSettings> = emptyMap()
) {
    val isSshConfigured: Boolean
        get() = sshHost.isNotBlank() && sshUser.isNotBlank() && sshPassword.isNotBlank()

    val isHermesConfigured: Boolean
        get() = hermesBaseUrl.isNotBlank() && hermesModel.isNotBlank() &&
            (!LlmProvider.fromId(hermesProvider).requiresKey || hermesApiKey.isNotBlank())

    fun withCurrentProviderSaved(): ServerConfig = copy(llmProviders = llmProviders +
        (hermesProvider to LlmProviderSettings(hermesBaseUrl, hermesApiKey, hermesModel)))

    fun selectProvider(provider: LlmProvider): ServerConfig {
        val saved = withCurrentProviderSaved()
        val next = saved.llmProviders[provider.id] ?: LlmProviderSettings(baseUrl = provider.baseUrl)
        return saved.copy(hermesProvider = provider.id, hermesBaseUrl = next.baseUrl,
            hermesApiKey = next.apiKey, hermesModel = next.model)
    }
}
