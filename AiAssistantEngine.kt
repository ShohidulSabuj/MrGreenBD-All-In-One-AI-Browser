package com.mrgreenbd.browser.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiAssistantEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun askAi(
        prompt: String,
        pageContext: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.success(
                buildOfflineSmartSummary(prompt, pageContext)
            )
        }

        try {
            val combinedPrompt = buildString {
                append("You are MrGreenBD AI+, the smart assistant built into MrGreenBD All in One AI+ Browser for Android Mobile, Tablet, and TV.\n")
                if (pageContext.isNotBlank()) {
                    append("Current Webpage Context:\n")
                    append(pageContext.take(4500))
                    append("\n\n")
                }
                append("User Request: $prompt")
            }

            val payload = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", combinedPrompt))
                        )
                    )
                )
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("API Error (${response.code}): $raw"))
                }
                val json = JSONObject(raw)
                val candidates = json.optJSONArray("candidates")
                val parts = candidates
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                val answer = parts?.optJSONObject(0)?.optString("text").orEmpty()
                Result.success(answer.ifBlank { "No response text returned." })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildOfflineSmartSummary(prompt: String, pageContext: String): String {
        val cleaned = pageContext.replace(Regex("\\s+"), " ").trim()
        val preview = if (cleaned.isEmpty()) {
            "No webpage text extracted yet. Open any webpage first, or enter a Gemini API key in AI+ for full cloud AI answers and Bangla translation."
        } else {
            cleaned.take(700) + if (cleaned.length > 700) "..." else ""
        }
        return "MrGreenBD AI+ (Smart Local Mode)\nRequest: $prompt\n\nPage Context Preview:\n$preview"
    }
}
