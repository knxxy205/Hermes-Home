package com.example.data.security

import android.content.Context
import com.jcraft.jsch.JSch
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File

/** One known-hosts store for terminal, SFTP, and agent commands. */
object SshTrust {
    data class Request(val message: String, val answer: CompletableDeferred<Boolean>)
    val pending = MutableStateFlow<Request?>(null)
    private lateinit var knownHosts: File
    private val promptLock = java.util.concurrent.locks.ReentrantLock()

    fun initialize(context: Context) {
        knownHosts = File(context.noBackupFilesDir, "known_hosts")
        if (!knownHosts.exists()) knownHosts.createNewFile()
    }

    fun configure(jsch: JSch): UserInfo {
        jsch.setKnownHosts(knownHosts.absolutePath)
        return object : UserInfo {
            override fun getPassphrase(): String? = null
            override fun getPassword(): String? = null
            override fun promptPassword(message: String?) = false
            override fun promptPassphrase(message: String?) = false
            override fun showMessage(message: String?) { }
            override fun promptYesNo(message: String?): Boolean {
                // A previously trusted key changing is never accepted automatically.
                if (message.orEmpty().contains("IDENTIFICATION HAS CHANGED", ignoreCase = true) ||
                    message.orEmpty().contains("replace the key", ignoreCase = true)) return false
                promptLock.lock()
                return try {
                    val request = Request(message.orEmpty(), CompletableDeferred())
                    pending.value = request
                    runBlocking { kotlinx.coroutines.withTimeoutOrNull(120_000) { request.answer.await() } ?: false }
                } finally { pending.value = null; promptLock.unlock() }
            }
        }
    }
}
