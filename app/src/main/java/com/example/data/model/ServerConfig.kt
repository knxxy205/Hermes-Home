package com.example.data.model

data class ServerConfig(
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUser: String = "",
    val sshPassword: String = "",
    val hermesBaseUrl: String = "http://100.100.100.100:11434",
    val hermesApiKey: String = "",
    val hermesModel: String = "hermes-3-llama-3.1-8b"
) {
    val isSshConfigured: Boolean
        get() = sshHost.isNotBlank() && sshUser.isNotBlank() && sshPassword.isNotBlank()

    val isHermesConfigured: Boolean
        get() = hermesBaseUrl.isNotBlank() && hermesModel.isNotBlank()
}
