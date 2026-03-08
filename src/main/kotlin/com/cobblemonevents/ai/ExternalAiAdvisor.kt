package com.cobblemonevents.ai

import com.cobblemonevents.CobblemonEventsMod
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class ExternalCandidate(
    val id: String,
    val mode: String,
    val displayName: String,
    val localScore: Double,
    val suggestedTarget: Int
)

data class ExternalAdviceInput(
    val conceptPrompt: String,
    val profileId: String?,
    val profilePrompt: String?,
    val playerCount: Int,
    val averagePartyLevel: Double,
    val timCoreTaggedNearby: Int,
    val candidates: List<ExternalCandidate>
)

data class ExternalAdvice(
    val preferredTemplateId: String? = null,
    val targetDelta: Int = 0,
    val confidence: Double = 0.0,
    val reason: String = ""
)

object ExternalAiAdvisor {
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4))
        .build()

    fun requestTemplateAdvice(input: ExternalAdviceInput): ExternalAdvice? {
        val cfg = AiProfileRegistry.getAdvisorConfig()
        if (!cfg.enabled || cfg.endpoint.isBlank() || input.candidates.isEmpty()) {
            return null
        }

        return runCatching {
            val payload = mapOf(
                "type" to "template_advice",
                "instruction" to "Choose one candidate id and optional targetDelta. Return JSON: {preferredTemplateId,targetDelta,confidence,reason}",
                "model" to cfg.model,
                "context" to mapOf(
                    "conceptPrompt" to input.conceptPrompt,
                    "profileId" to input.profileId,
                    "profilePrompt" to input.profilePrompt,
                    "playerCount" to input.playerCount,
                    "averagePartyLevel" to input.averagePartyLevel,
                    "timCoreTaggedNearby" to input.timCoreTaggedNearby
                ),
                "candidates" to input.candidates
            )

            val body = postJson(cfg, payload) ?: return null
            parseAdvice(body)
        }.onFailure {
            CobblemonEventsMod.LOGGER.debug("[AI Advisor] request failed: ${it.message}")
        }.getOrNull()
    }

    fun testConnection(): Pair<Boolean, String> {
        val cfg = AiProfileRegistry.getAdvisorConfig()
        if (!cfg.enabled) return false to "advisor disabled"
        if (cfg.endpoint.isBlank()) return false to "endpoint empty"

        return runCatching {
            val payload = mapOf(
                "type" to "health_check",
                "timestamp" to System.currentTimeMillis(),
                "model" to cfg.model
            )
            val body = postJson(cfg, payload)
            if (body.isNullOrBlank()) {
                false to "empty response"
            } else {
                true to "ok"
            }
        }.getOrElse {
            false to (it.message ?: "request failed")
        }
    }

    private fun postJson(cfg: AiExternalAdvisorConfig, payload: Any): String? {
        val endpoint = runCatching { URI(cfg.endpoint) }.getOrNull() ?: return null

        val requestBuilder = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofMillis(cfg.timeoutMs.toLong().coerceAtLeast(500L)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))

        if (cfg.bearerToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${cfg.bearerToken}")
        }
        if (cfg.model.isNotBlank()) {
            requestBuilder.header("X-Model", cfg.model)
        }

        val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            CobblemonEventsMod.LOGGER.debug("[AI Advisor] HTTP ${response.statusCode()} from ${cfg.endpoint}")
            return null
        }
        return response.body()
    }

    private fun parseAdvice(body: String): ExternalAdvice? {
        parseAdviceFromRawJson(body)?.let { return it }

        val root = runCatching { JsonParser.parseString(body) }.getOrNull() ?: return null
        if (!root.isJsonObject) return null
        val rootObj = root.asJsonObject

        // OpenAI-like response: choices[0].message.content
        if (rootObj.has("choices")) {
            val content = rootObj.getAsJsonArray("choices")
                .firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")
                ?.asString
            if (!content.isNullOrBlank()) {
                parseAdviceFromRawJson(content)?.let { return it }
            }
        }

        // Responses API style: output_text
        val outputText = rootObj.get("output_text")?.let { it.asString }
        if (!outputText.isNullOrBlank()) {
            parseAdviceFromRawJson(outputText)?.let { return it }
        }

        return null
    }

    private fun parseAdviceFromRawJson(raw: String): ExternalAdvice? {
        val jsonText = extractLikelyJson(raw) ?: raw
        val parsed = runCatching { JsonParser.parseString(jsonText) }.getOrNull() ?: return null
        if (!parsed.isJsonObject) return null
        return parseAdviceObject(parsed.asJsonObject)
    }

    private fun parseAdviceObject(obj: JsonObject): ExternalAdvice? {
        val preferredTemplateId = firstString(obj, "preferredTemplateId", "templateId", "id")
        val targetDelta = firstInt(obj, "targetDelta", "delta", "targetAdjust") ?: 0
        val confidence = firstDouble(obj, "confidence", "score") ?: 0.5
        val reason = firstString(obj, "reason", "note") ?: ""

        if (preferredTemplateId == null && targetDelta == 0 && reason.isBlank()) {
            return null
        }

        return ExternalAdvice(
            preferredTemplateId = preferredTemplateId,
            targetDelta = targetDelta.coerceIn(-6, 6),
            confidence = confidence.coerceIn(0.0, 1.0),
            reason = reason.take(240)
        )
    }

    private fun extractLikelyJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun firstString(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val value = obj.get(key) ?: continue
            if (value.isJsonNull) continue
            return runCatching { value.asString }.getOrNull()
        }
        return null
    }

    private fun firstInt(obj: JsonObject, vararg keys: String): Int? {
        for (key in keys) {
            val value = obj.get(key) ?: continue
            if (value.isJsonNull) continue
            runCatching { value.asInt }.getOrNull()?.let { return it }
            runCatching { value.asDouble.toInt() }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun firstDouble(obj: JsonObject, vararg keys: String): Double? {
        for (key in keys) {
            val value = obj.get(key) ?: continue
            if (value.isJsonNull) continue
            runCatching { value.asDouble }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun Iterable<JsonElement>.firstOrNull(): JsonElement? {
        val iterator = iterator()
        return if (iterator.hasNext()) iterator.next() else null
    }
}
