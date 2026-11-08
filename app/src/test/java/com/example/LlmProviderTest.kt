package com.example

import com.example.data.hermes.HermesApiClient
import com.example.data.model.LlmProvider
import com.example.data.model.ServerConfig
import com.example.data.security.decodeProviders
import com.example.data.security.encodeProviders
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(SshAwareRobolectricTestRunner::class)
@Config(sdk = [34])
class LlmProviderTest {
    @Test fun changingProvidersRestoresTheirOwnCredentialsAndCustomServer() {
        val custom = ServerConfig(hermesBaseUrl = "https://server.example/proxy/v1", hermesApiKey = "custom-fixture", hermesModel = "local-model")
        val google = custom.selectProvider(LlmProvider.GOOGLE)
        assertEquals(LlmProvider.GOOGLE.baseUrl, google.hermesBaseUrl)
        assertEquals("", google.hermesApiKey)
        assertEquals("", google.hermesModel)
        assertFalse(google.isHermesConfigured)
        val configured = google.copy(hermesApiKey = "google-fixture", hermesModel = "fixture-gemini")
        val openrouter = configured.selectProvider(LlmProvider.OPENROUTER)
        assertEquals("", openrouter.hermesApiKey)
        assertEquals("google-fixture", openrouter.selectProvider(LlmProvider.GOOGLE).hermesApiKey)
        val restored = openrouter.selectProvider(LlmProvider.CUSTOM)
        assertEquals(custom.hermesBaseUrl, restored.hermesBaseUrl)
        assertEquals(custom.hermesApiKey, restored.hermesApiKey)
        assertEquals(custom.hermesModel, restored.hermesModel)
        assertEquals(restored.llmProviders, decodeProviders(encodeProviders(restored.llmProviders).toString()))
    }

    @Test fun providerPathsAndBearerHeadersReachModelsAndChatWithoutExtraV1() = runBlocking {
        MockWebServer().use { server ->
            val paths = mapOf(LlmProvider.GOOGLE to "/v1beta/openai", LlmProvider.OPENROUTER to "/api/v1", LlmProvider.OPENAI to "/v1")
            for ((provider, path) in paths) {
                val config = ServerConfig(hermesProvider = provider.id, hermesBaseUrl = server.url("$path/").toString(), hermesApiKey = "fixture-${provider.id}", hermesModel = "fixture-model")
                server.enqueue(MockResponse().setBody("{\"data\":[{\"id\":\"fixture-model\"}]}"))
                assertEquals(listOf("fixture-model"), HermesApiClient().fetchAvailableModels(config).getOrThrow())
                server.takeRequest().let { request ->
                    assertEquals("$path/models", request.path)
                    assertEquals("Bearer fixture-${provider.id}", request.getHeader("Authorization"))
                }
                server.enqueue(MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"Siap\"}}]}"))
                assertEquals("Siap", HermesApiClient().sendMessage("Halo", emptyList(), config).getOrThrow())
                server.takeRequest().let { request ->
                    assertEquals("$path/chat/completions", request.path)
                    assertEquals("Bearer fixture-${provider.id}", request.getHeader("Authorization"))
                    assertEquals("fixture-model", JSONObject(request.body.readUtf8()).getString("model"))
                }
            }
        }
    }

    @Test fun missingKeysFailBeforeRequestAndQuotaErrorsExplainTheNextAction() = runBlocking {
        MockWebServer().use { server ->
            val config = ServerConfig(hermesProvider = "google", hermesBaseUrl = server.url("/v1beta/openai").toString(), hermesModel = "fixture-model")
            assertTrue(HermesApiClient().sendMessage("Halo", emptyList(), config).isFailure)
            assertTrue(HermesApiClient().fetchAvailableModels(config).isFailure)
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(429).setBody("{\"error\":{\"message\":\"fixture-key\"}}"))
            val result = HermesApiClient().sendMessage("Halo", emptyList(), config.copy(hermesApiKey = "fixture-key"))
            assertTrue(result.exceptionOrNull()!!.message!!.contains("kuota"))
            assertFalse(result.exceptionOrNull()!!.message!!.contains("fixture-key"))
        }
    }

    @Test fun connectionCheckRequiresAChatReplyAndAcceptsFullCompletionUrls() = runBlocking {
        MockWebServer().use { server ->
            val config = ServerConfig(hermesBaseUrl = server.url("/proxy/chat/completions").toString(), hermesModel = "fixture-model")
            server.enqueue(MockResponse().setBody("{}"))
            assertTrue(HermesApiClient().testConnection(config).isFailure)
            assertEquals("/proxy/chat/completions", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}"))
            assertTrue(HermesApiClient().testConnection(config).isSuccess)
            server.takeRequest()
            assertTrue(HermesApiClient().testConnection(config.copy(hermesBaseUrl = server.url("/?key=fixture").toString())).isFailure)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun claudeUsesNativeMessagesAuthAndOnlyDisplaysTextBlocks() = runBlocking {
        MockWebServer().use { server ->
            val config = ServerConfig(hermesProvider = "claude", hermesBaseUrl = server.url("/v1").toString(), hermesApiKey = "claude-fixture", hermesModel = "fixture-claude")
            val api = HermesApiClient()
            server.enqueue(MockResponse().setBody("{\"data\":[{\"id\":\"fixture-claude\"}]}"))
            assertEquals(listOf("fixture-claude"), api.fetchAvailableModels(config).getOrThrow())
            server.takeRequest().let { request ->
                assertEquals("/v1/models", request.path)
                assertEquals("claude-fixture", request.getHeader("x-api-key"))
                assertEquals("2023-06-01", request.getHeader("anthropic-version"))
                assertNull(request.getHeader("Authorization"))
            }
            val reply = "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"private\"},{\"type\":\"text\",\"text\":\"Halo\"},{\"type\":\"text\",\"text\":\"Siap.\"}]}"
            server.enqueue(MockResponse().setBody(reply))
            val history = listOf(com.example.data.hermes.ChatMessage(role = "assistant", content = "Welcome"),
                com.example.data.hermes.ChatMessage(role = "user", content = "Pertama"),
                com.example.data.hermes.ChatMessage(role = "assistant", content = "Balasan"))
            assertEquals("Halo\nSiap.", api.sendMessage("Berikutnya", history, config, responseInstructions = "Ringkas").getOrThrow())
            server.takeRequest().let { request ->
                assertEquals("/v1/messages", request.path)
                assertEquals("claude-fixture", request.getHeader("x-api-key"))
                val body = JSONObject(request.body.readUtf8())
                assertTrue(body.getString("system").contains("Ringkas"))
                assertEquals(4096, body.getInt("max_tokens"))
                assertEquals(3, body.getJSONArray("messages").length())
                assertEquals("user", body.getJSONArray("messages").getJSONObject(0).getString("role"))
            }
            server.enqueue(MockResponse().setBody(reply))
            assertTrue(api.testConnection(config).isSuccess)
            assertEquals("/v1/messages", server.takeRequest().path)
        }
    }

    @Test fun reasoningEffortUsesProviderFieldOnlyForKnownSupportedModelFamilies() = runBlocking {
        MockWebServer().use { server ->
            val cases = listOf(
                Triple("openai", "gpt-6-astra", "low"),
                Triple("google", "gemini-3.8-flash", "medium"),
                Triple("claude", "claude-opus-4-6", "high"),
                Triple("openrouter", "anthropic/claude-opus-5.5", "medium"),
                Triple("custom", "my-unknown-router-model", "high")
            )
            for ((provider, model, effort) in cases) {
                val config = ServerConfig(
                    hermesProvider = provider,
                    hermesBaseUrl = server.url(if (provider == "google") "/v1beta/openai" else "/v1").toString(),
                    hermesApiKey = "fixture-key",
                    hermesModel = model
                )
                val response = if (provider == "claude")
                    "{\"content\":[{\"type\":\"text\",\"text\":\"Siap\"}]}"
                else "{\"choices\":[{\"message\":{\"content\":\"Siap\"}}]}"
                server.enqueue(MockResponse().setBody(response))
                assertEquals("Siap", HermesApiClient().sendMessage("Halo", emptyList(), config, reasoningEffort = effort).getOrThrow())
                val request = server.takeRequest()
                val body = JSONObject(request.body.readUtf8())
                when (provider) {
                    "claude" -> assertEquals(effort, body.getJSONObject("output_config").getString("effort"))
                    "openrouter" -> assertEquals(effort, body.getJSONObject("reasoning").getString("effort"))
                    "custom" -> assertFalse(body.has("reasoning_effort"))
                    else -> assertEquals(effort, body.getString("reasoning_effort"))
                }
            }

            val unsupported = ServerConfig(hermesProvider = "openai", hermesBaseUrl = server.url("/v1").toString(), hermesApiKey = "fixture-key", hermesModel = "gpt-4o")
            server.enqueue(MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"Default\"}}]}"))
            HermesApiClient().sendMessage("Halo", emptyList(), unsupported, reasoningEffort = "high").getOrThrow()
            assertFalse(JSONObject(server.takeRequest().body.readUtf8()).has("reasoning_effort"))
        }
    }
}
