package com.example

import com.example.data.hermes.HermesApiClient
import com.example.data.hermes.HermesEndpointPolicy
import com.example.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.util.concurrent.TimeUnit

@RunWith(SshAwareRobolectricTestRunner::class)
@Config(sdk = [34])
class HermesEndpointPolicyTest {
    @Test fun publicHttpIncludingFcAndFdDomainNamesIsRejected() {
        listOf("http://fc-public.example", "http://fd-public.example", "http://8.8.8.8", "http://[2606:4700:4700::1111]",
            "http://100.128.0.1", "http://172.32.0.1").forEach {
            assertTrue(it, HermesEndpointPolicy.validate(it).isFailure)
        }
    }

    @Test fun privateHostsAndPublicHttpsAreAccepted() {
        listOf("http://127.0.0.1", "http://192.168.1.10", "http://100.64.0.1", "http://100.127.255.254",
            "http://172.16.0.1", "http://[::1]", "http://[fd00::1]", "http://node.example.ts.net", "https://api.example.com").forEach {
            assertTrue(it, HermesEndpointPolicy.validate(it).isSuccess)
        }
        assertTrue(HermesEndpointPolicy.validate("https://user@example.com").isFailure)
    }

    @Test fun resolvedPublicAddressesCannotMasqueradeAsLocalNames() {
        assertFalse(HermesEndpointPolicy.isPrivateAddress(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))))
        assertFalse(HermesEndpointPolicy.isPrivateAddress(InetAddress.getByName("2606:4700:4700::1111")))
        assertTrue(HermesEndpointPolicy.isPrivateAddress(InetAddress.getByAddress(byteArrayOf(100, 64, 0, 1))))
    }

    @Test fun redirectsAreCheckedBeforeSendingAnotherRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", "http://fc-public.example:${server.port}/collect"))
            server.enqueue(MockResponse().setBody("should never be reached"))
            val client = OkHttpClient.Builder().dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getByName("127.0.0.1"))
            })
                .readTimeout(2, TimeUnit.SECONDS).build()
            val api = HermesApiClient(client)
            val result = api.testConnection(ServerConfig(hermesBaseUrl = server.url("/").toString(), hermesModel = "test-model"))
            assertTrue(result.isFailure)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun directPrivateServerStillWorks() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}"))
            val api = HermesApiClient()
            assertTrue(api.testConnection(ServerConfig(hermesBaseUrl = server.url("/").toString(), hermesModel = "test-model")).isSuccess)
            assertEquals("/v1/chat/completions", server.takeRequest().path)
        }
    }
}
