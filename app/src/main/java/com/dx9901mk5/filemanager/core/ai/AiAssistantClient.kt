package com.dx9901mk5.filemanager.core.ai

import com.dx9901mk5.filemanager.core.settings.AiProvider
import com.dx9901mk5.filemanager.core.vfs.VfsFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiMoveInstruction(
    val fileName: String,
    val targetFolder: String
)

data class AiOrganizeResult(
    val summary: String,
    val moves: List<AiMoveInstruction> = emptyList()
)

object AiAssistantClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeAndPlan(
        provider: AiProvider,
        apiKey: String,
        currentPath: String,
        files: List<VfsFile>,
        userPrompt: String
    ): Result<AiOrganizeResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "請先輸入 ${provider.displayName} API 金鑰" }

            val fileSummary = files.take(150).joinToString("\n") { f ->
                val kind = if (f.isDirectory) "DIR" else "FILE (${f.size} bytes)"
                "- ${f.name} [$kind]"
            }

            val systemInstruction = """
                你是 DX檔案管理器 的專業 AI 檔案分析與整理助手。請用繁體中文簡潔清楚回答。
                目前目錄：$currentPath
                項目清單（最多顯示 150 筆）：
                $fileSummary

                如果使用者的要求涉及「分類整理、移動、歸納檔案到資料夾」，請在回覆最後加上一段 JSON（用 ```json ... ``` 包起來），格式如下：
                ```json
                {"moves":[{"file":"完整檔名","targetFolder":"建議資料夾名稱"}]}
                ```
                只針對清單中已存在的檔案給出 moves；若只是分析統計、不需移動檔案，則不需要輸出 JSON。
            """.trimIndent()

            val rawText = when (provider) {
                AiProvider.GEMINI -> callGemini(apiKey.trim(), provider.defaultModel, systemInstruction, userPrompt)
                AiProvider.GPT -> callOpenAiCompatible(
                    url = "https://api.openai.com/v1/chat/completions",
                    apiKey = apiKey.trim(),
                    model = provider.defaultModel,
                    systemPrompt = systemInstruction,
                    userPrompt = userPrompt
                )
                AiProvider.GROK -> callOpenAiCompatible(
                    url = "https://api.x.ai/v1/chat/completions",
                    apiKey = apiKey.trim(),
                    model = provider.defaultModel,
                    systemPrompt = systemInstruction,
                    userPrompt = userPrompt
                )
                AiProvider.CLAUDE -> callClaude(apiKey.trim(), provider.defaultModel, systemInstruction, userPrompt)
            }

            parseAiResponse(rawText)
        }
    }

    private fun callGemini(apiKey: String, model: String, systemPrompt: String, userPrompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", "$systemPrompt\n\n使用者需求：$userPrompt")))
            }))
        }
        val req = Request.Builder().url(url).post(body.toString().toRequestBody(jsonMedia)).build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Gemini 錯誤 (${resp.code})：${raw.take(200)}")
            val json = JSONObject(raw)
            return json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()
                .ifBlank { "AI 未回傳文字內容" }
        }
    }

    private fun callOpenAiCompatible(
        url: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                put(JSONObject().put("role", "user").put("content", userPrompt))
            })
        }
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("AI API 錯誤 (${resp.code})：${raw.take(200)}")
            val json = JSONObject(raw)
            return json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .ifBlank { "AI 未回傳文字內容" }
        }
    }

    private fun callClaude(apiKey: String, model: String, systemPrompt: String, userPrompt: String): String {
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 2048)
            put("system", systemPrompt)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", userPrompt)))
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Claude 錯誤 (${resp.code})：${raw.take(200)}")
            val json = JSONObject(raw)
            return json.optJSONArray("content")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()
                .ifBlank { "AI 未回傳文字內容" }
        }
    }

    private fun parseAiResponse(rawText: String): AiOrganizeResult {
        val jsonBlockRegex = Regex("```json\\s*(\\{[\\s\\S]*?\\})\\s*```")
        val match = jsonBlockRegex.find(rawText)
        val moves = if (match != null) {
            runCatching {
                val obj = JSONObject(match.groupValues[1])
                val arr = obj.optJSONArray("moves") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val file = item.optString("file").trim()
                        val folder = item.optString("targetFolder").trim()
                        if (file.isNotEmpty() && folder.isNotEmpty()) {
                            add(AiMoveInstruction(fileName = file, targetFolder = folder))
                        }
                    }
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val cleanSummary = if (match != null) rawText.replace(match.value, "").trim() else rawText.trim()
        return AiOrganizeResult(summary = cleanSummary, moves = moves)
    }
}
