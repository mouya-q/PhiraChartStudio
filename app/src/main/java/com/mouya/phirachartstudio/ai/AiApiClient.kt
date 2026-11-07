package com.mouya.phirachartstudio.ai

import com.mouya.phirachartstudio.engine.AnalysisResult
import com.mouya.phirachartstudio.engine.ArrangementHints
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI

class AiApiClient {
    data class Settings(val baseUrl: String, val model: String, val apiKey: String)
    data class ModelList(val models: List<String>)

    fun testConnection(settings: Settings): String {
        val models = fetchModels(settings)
        return "连接成功 · 读取到 ${models.models.size} 个模型"
    }

    fun fetchModels(settings: Settings): ModelList {
        val response = request(settings, "GET", "/models", null)
        val data = JSONObject(response).optJSONArray("data") ?: JSONArray()
        val models = buildList {
            for (i in 0 until data.length()) {
                val id = data.optJSONObject(i)?.optString("id")?.trim().orEmpty()
                if (id.isNotBlank()) add(id)
            }
        }.distinct().sorted()
        return ModelList(models)
    }

    /** Sends only compact, derived audio features; never reads or uploads the source audio. */
    fun suggestArrangement(settings: Settings, analysis: AnalysisResult): ArrangementHints {
        val featureSummary = JSONObject().apply {
            put("bpm", analysis.bpm.toDouble())
            put("tempo_confidence", analysis.confidence.toDouble())
            put("duration_seconds", analysis.durationSeconds.toDouble())
            put("onset_count", analysis.onsetTimesSeconds.size)
            put("onset_times_sample", JSONArray(analysis.onsetTimesSeconds.filterIndexed { index, _ -> index % 4 == 0 }.take(160)))
            put("energy_segments", JSONArray().apply {
                analysis.segments.take(40).forEach { segment ->
                    put(JSONObject().put("start", segment.startSeconds).put("end", segment.endSeconds)
                        .put("energy", segment.averageEnergy).put("label", segment.label))
                }
            })
        }
        val system = """You are a rhythm-game chart arrangement assistant. Return only one JSON object with numeric keys: densityMultiplier (0.75..1.25), holdBias (0..0.18), flickBias (0..0.28), syncopation (0..0.30). These are subtle bounded hints for a local deterministic generator. Do not return notes, code, markdown, prose, or additional keys. Keep rhythm playable and respect musical phrasing."""
        val body = JSONObject().apply {
            put("model", settings.model)
            put("temperature", 0.2)
            put("max_tokens", 180)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", featureSummary.toString())))
        }
        val response = request(settings, "POST", "/chat/completions", body.toString())
        val content = JSONObject(response).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        val json = JSONObject(content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        return ArrangementHints(
            densityMultiplier = json.optDouble("densityMultiplier", 1.0).toFloat(),
            holdBias = json.optDouble("holdBias", 0.08).toFloat(),
            flickBias = json.optDouble("flickBias", 0.12).toFloat(),
            syncopation = json.optDouble("syncopation", 0.18).toFloat(),
        ).validated()
    }

    private fun request(settings: Settings, method: String, endpoint: String, body: String?): String {
        require(settings.apiKey.isNotBlank()) { "尚未配置 API Key" }
        require(settings.model.isNotBlank()) { "尚未配置模型 ID" }
        val base = settings.baseUrl.trim().trimEnd('/')
        require(base.length <= 512) { "AI 服务地址过长" }
        val uri = runCatching { URI(base) }.getOrElse { throw IllegalArgumentException("AI 服务地址格式无效") }
        require(uri.scheme?.equals("https", ignoreCase = true) == true && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null && uri.rawQuery == null && !uri.isOpaque && (uri.port == -1 || uri.port in 1..65535)) {
            "AI 服务地址必须是有效的 HTTPS 地址，且不能包含用户信息、查询参数或片段"
        }
        val requestUrl = URL(base + endpoint)
        var lastError: Throwable? = null
        repeat(2) { attempt ->
            var connection: HttpURLConnection? = null
            try {
                connection = requestUrl.openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.connectTimeout = 12_000
                connection.readTimeout = 18_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "PhiraChartStudio/1.0")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val retryable = status == 408 || status == 429 || status in 500..599
                    if (retryable && attempt == 0) {
                        lastError = IOException("AI 服务返回 HTTP $status")
                        return@repeat
                    }
                    if (retryable) throw IOException("AI 服务返回 HTTP $status")
                    throw NonRetryableApiResponseException("AI 服务返回 HTTP $status")
                }
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val result = StringBuilder()
                    val buffer = CharArray(8192)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        require(result.length + count <= MAX_RESPONSE_CHARS) { "AI 响应超过安全长度限制" }
                        result.append(buffer, 0, count)
                    }
                    result.toString()
                }
                return response
            } catch (error: Throwable) {
                lastError = error
                if (attempt == 1 || error !is IOException) throw error
            } finally {
                connection?.disconnect()
            }
        }
        throw IOException("AI 请求失败，请检查网络与服务配置", lastError)
    }

    private class NonRetryableApiResponseException(message: String) : Exception(message)

    private companion object { const val MAX_RESPONSE_CHARS = 256 * 1024 }
}
