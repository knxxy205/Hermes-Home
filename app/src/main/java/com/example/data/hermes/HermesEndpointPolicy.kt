package com.example.data.hermes

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy

internal object HermesEndpointPolicy {
    fun validate(rawUrl: String): Result<Unit> = runCatching {
        val url = rawUrl.trim().toHttpUrlOrNull() ?: throw IOException("URL Hermes tidak valid; gunakan HTTP atau HTTPS")
        checkUrl(url)
    }

    private fun checkUrl(url: HttpUrl) {
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw IOException("Jangan masukkan username atau password ke dalam URL")
        }
        if (!url.isHttps && !isLocalHost(url.host)) {
            throw IOException("HTTP hanya diizinkan untuk server lokal atau privat. Gunakan HTTPS untuk host publik.")
        }
    }

    private fun isLocalHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
            host.endsWith(".lan") || host.endsWith(".ts.net")) return true
        // A public domain beginning with fc/fd is not private IPv6.
        if (':' in host) return runCatching { isPrivateAddress(InetAddress.getByName(host)) }.getOrDefault(false)
        val parts = host.split('.')
        if (parts.size != 4) return false
        val bytes = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return false }
        return privateIpv4(bytes[0], bytes[1])
    }

    private fun privateIpv4(first: Int, second: Int): Boolean = first == 10 || first == 127 ||
        (first == 169 && second == 254) || (first == 172 && second in 16..31) ||
        (first == 192 && second == 168) || (first == 100 && second in 64..127)

    internal fun isPrivateAddress(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size == 4) return privateIpv4(bytes[0].toInt() and 255, bytes[1].toInt() and 255)
        val first = bytes[0].toInt() and 255
        val second = bytes[1].toInt() and 255
        return address.isLoopbackAddress || (first and 0xfe) == 0xfc ||
            (first == 0xfe && (second and 0xc0) == 0x80)
    }

    /** Check every redirect before its request headers and body are sent. */
    val interceptor = Interceptor { chain ->
        val request = chain.request()
        checkUrl(request.url)
        if (!request.url.isHttps) {
            val route = chain.connection()?.route() ?: throw IOException("Tidak dapat memverifikasi koneksi HTTP privat")
            if (route.proxy.type() != Proxy.Type.DIRECT || !isPrivateAddress(route.socketAddress.address)) {
                throw IOException("Tujuan HTTP harus berada di jaringan privat. Gunakan HTTPS untuk tujuan ini.")
            }
        }
        chain.proceed(request)
    }
}
