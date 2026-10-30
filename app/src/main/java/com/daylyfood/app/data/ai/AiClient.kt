package com.daylyfood.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single exit point for every model call: one auth path, one timeout policy,
 * one daily budget, one error vocabulary. The endpoint and model come from settings.
 */
@Singleton
class AiClient @Inject constructor(
    private val settings: AiSettings,
) {

    /** Sends a trivial prompt so the user can verify URL + model + key before using any agent. */
    suspend fun testConnection(): Result<String> = runCall { config, key ->
        post(
            url = config.endpointUrl,
            apiKey = key,
            body = textBody(config.modelName, "你是连通性测试助手。只回复两个字：可用", "测试一下", maxTokens = 32),
        )
    }

    suspend fun completeText(system: String, user: String): Result<String> = runCall { config, key ->
        post(
            url = config.endpointUrl,
            apiKey = key,
            body = textBody(config.modelName, system, user, maxTokens = 800),
        )
    }

    suspend fun completeVision(system: String, prompt: String, jpegBase64: String): Result<String> =
        runCall { config, key ->
            post(
                url = config.endpointUrl,
                apiKey = key,
                body = visionBody(config.modelName, system, prompt, jpegBase64),
            )
        }

    private suspend fun runCall(block: (AiConfig, String) -> String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val config = settings.config.value
                if (config.endpointUrl.isBlank() || config.modelName.isBlank()) {
                    error("还没有填写接口地址和模型名")
                }
                if (!CallBudget.canCall(config.usedToday, config.dailyCallLimit)) {
                    error("今日调用次数已用完，明天再试或调高上限")
                }
                val key = settings.readKey() ?: error("还没有填写 API Key")
                val text = block(config, key)
                settings.recordCall()
                text
            }
        }

    private fun textBody(model: String, system: String, user: String, maxTokens: Int): String =
        JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
            .put("temperature", 0.2)
            .put("max_tokens", maxTokens)
            .toString()

    private fun visionBody(
        model: String,
        system: String,
        prompt: String,
        jpegBase64: String,
    ): String = JSONObject()
        .put("model", model)
        .put(
            "messages",
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(
                    JSONObject().put("role", "user").put(
                        "content",
                        JSONArray()
                            .put(JSONObject().put("type", "text").put("text", prompt))
                            .put(
                                JSONObject().put("type", "image_url").put(
                                    "image_url",
                                    JSONObject().put("url", "data:image/jpeg;base64,$jpegBase64"),
                                ),
                            ),
                    ),
                ),
        )
        .put("temperature", 0.1)
        .put("max_tokens", 1000)
        .toString()

    private fun post(url: String, apiKey: String, body: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            setRequestProperty("User-Agent", "dayly-food")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error(describe(code, text))

            val content = JSONObject(text)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (content.isBlank()) error("模型没有返回内容")
            return content
        } finally {
            connection.disconnect()
        }
    }

    private fun describe(code: Int, body: String): String {
        val detail = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val reason = when (code) {
            401, 403 -> "Key 无效或没有该模型权限"
            404 -> "地址或模型名不对"
            429 -> "调用太频繁，稍后再试"
            in 500..599 -> "服务端繁忙"
            else -> "调用失败 HTTP $code"
        }
        return if (detail != null) "$reason（$detail）" else reason
    }
}
