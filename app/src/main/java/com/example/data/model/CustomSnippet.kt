package com.example.data.model

import java.util.UUID

data class CustomSnippet(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val command: String,
    val description: String = "",
    val category: String = "Umum",
    val isDefault: Boolean = false
) {
    companion object {
        fun getDefaultSnippets(): List<CustomSnippet> = listOf(
            CustomSnippet(
                id = "default_uname",
                name = "Sys Info",
                command = "uname -a",
                description = "Informasi kernel dan sistem operasi",
                category = "Sistem",
                isDefault = true
            ),
            CustomSnippet(
                id = "default_docker",
                name = "Docker PS",
                command = "docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Ports}}'",
                description = "Daftar container docker yang berjalan",
                category = "Docker",
                isDefault = true
            ),
            CustomSnippet(
                id = "default_df",
                name = "Disk Space",
                command = "df -h",
                description = "Kapasitas penyimpanan partisi disk",
                category = "Storage",
                isDefault = true
            ),
            CustomSnippet(
                id = "default_free",
                name = "RAM Free",
                command = "free -h",
                description = "Penggunaan memori RAM & Swap",
                category = "Sistem",
                isDefault = true
            ),
            CustomSnippet(
                id = "default_uptime",
                name = "Uptime",
                command = "uptime -p",
                description = "Berapa lama server telah menyala",
                category = "Sistem",
                isDefault = true
            ),
            CustomSnippet(
                id = "default_tailscale",
                name = "Tailscale",
                command = "tailscale status",
                description = "Status konektivitas mesh Tailscale",
                category = "Jaringan",
                isDefault = true
            ),
            CustomSnippet(
                id = "default_ports",
                name = "Open Ports",
                command = "ss -tulwn",
                description = "Daftar port TCP/UDP yang sedang listening",
                category = "Jaringan",
                isDefault = true
            ),
            CustomSnippet(
                id = "default_logs",
                name = "Syslog",
                command = "journalctl -n 30 --no-pager",
                description = "30 baris log sistem terbaru",
                category = "Log",
                isDefault = true
            )
        )
    }
}
