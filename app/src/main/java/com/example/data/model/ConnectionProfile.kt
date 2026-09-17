package com.example.data.model

data class ConnectionProfile(
    val id: String,
    val name: String,
    val config: ServerConfig
) {
    companion object {
        const val PROFILE_HOME_ID = "profile_home"
        const val PROFILE_TAILSCALE_ID = "profile_tailscale"

        fun defaultHomeProfile(): ConnectionProfile = ConnectionProfile(
            id = PROFILE_HOME_ID,
            name = "Rumah (LAN)",
            config = ServerConfig(
                sshHost = "192.168.1.100",
                sshPort = 22,
                sshUser = "admin",
                sshPassword = "",
                hermesBaseUrl = "https://192.168.1.100:11434",
                hermesApiKey = "",
                hermesModel = "hermes-3-llama-3.1-8b"
            )
        )

        fun defaultTailscaleProfile(): ConnectionProfile = ConnectionProfile(
            id = PROFILE_TAILSCALE_ID,
            name = "Di Luar (Tailscale)",
            config = ServerConfig(
                sshHost = "100.x.y.z",
                sshPort = 22,
                sshUser = "admin",
                sshPassword = "",
                hermesBaseUrl = "https://100.x.y.z:11434",
                hermesApiKey = "",
                hermesModel = "hermes-3-llama-3.1-8b"
            )
        )
    }
}
