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
        model: String = provider.defaultModel,
        thinkingEnabled: Boolean = false,
        currentPath: String,
        files: List<VfsFile>,
        userPrompt: String
    ): Result<AiOrganizeResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "請先輸入 ${provider.displayName} API 金鑰" }
            val chosenModel = model.trim().ifBlank { provider.defaultModel }

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
                AiProvider.GEMINI -> callGemini(apiKey.trim(), chosenModel, thinkingEnabled, systemInstruction, userPrompt)
                AiProvider.DEEPSEEK -> {
                    val dsModel = if (thinkingEnabled && chosenModel == "deepseek-chat") "deepseek-reasoner" else chosenModel
                    callOpenAiCompatible(
                        url = "https://api.deepseek.com/v1/chat/completions",
                        apiKey = apiKey.trim(),
                        model = dsModel,
                        thinkingEnabled = thinkingEnabled,
                        systemPrompt = systemInstruction,
                        userPrompt = userPrompt
                    )
                }
                AiProvider.GPT -> callOpenAiCompatible(
                    url = "https://api.openai.com/v1/chat/completions",
                    apiKey = apiKey.trim(),
                    model = chosenModel,
                    thinkingEnabled = thinkingEnabled,
                    systemPrompt = systemInstruction,
                    userPrompt = userPrompt
                )
                AiProvider.GROK -> callOpenAiCompatible(
                    url = "https://api.x.ai/v1/chat/completions",
                    apiKey = apiKey.trim(),
                    model = chosenModel,
                    thinkingEnabled = thinkingEnabled,
                    systemPrompt = systemInstruction,
                    userPrompt = userPrompt
                )
                AiProvider.CLAUDE -> callClaude(apiKey.trim(), chosenModel, thinkingEnabled, systemInstruction, userPrompt)
            }

            parseAiResponse(rawText)
        }
    }

    private fun callGemini(
        apiKey: String,
        model: String,
        thinkingEnabled: Boolean,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", "$systemPrompt\n\n使用者需求：$userPrompt")))
            }))
            if (model.startsWith("gemini-2.5") || model.startsWith("gemini-3") || model.startsWith("gemini-4")) {
                put(
                    "generationConfig",
                    JSONObject().put(
                        "thinkingConfig",
                        JSONObject().put("thinkingBudget", if (thinkingEnabled) 4096 else 0)
                    )
                )
            }
        }
        val req = Request.Builder().url(url).post(body.toString().toRequestBody(jsonMedia)).build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val friendly = when (resp.code) {
                    402 -> "Gemini API 專案預付額度已用盡 (402)。請至 AI Studio 檢查帳單或切換免費專案金鑰，或改用下方「本機智慧整理（免額度）」。"
                    401, 403 -> "Gemini API 金鑰無效或權限不足 (${resp.code})，請重新確認金鑰。"
                    429 -> "Gemini 請求過於頻繁或超過免費額度上限 (429)，請稍後再試或改用「本機智慧整理」。"
                    else -> "Gemini 錯誤 (${resp.code})：${raw.take(180)}"
                }
                throw IllegalStateException(friendly)
            }
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
        thinkingEnabled: Boolean,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val body = JSONObject().apply {
            put("model", model)
            if (thinkingEnabled && (model.startsWith("o1") || model.startsWith("o3") || model.startsWith("o4") || model == "grok-3-mini")) {
                put("reasoning_effort", "medium")
            }
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

    private fun callClaude(
        apiKey: String,
        model: String,
        thinkingEnabled: Boolean,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", if (thinkingEnabled) 4096 else 2048)
            put("system", systemPrompt)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", userPrompt)))
            if (thinkingEnabled) {
                put("thinking", JSONObject().put("type", "enabled").put("budget_tokens", 2048))
            }
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
            val contentArr = json.optJSONArray("content")
            if (contentArr != null) {
                for (i in 0 until contentArr.length()) {
                    val item = contentArr.optJSONObject(i) ?: continue
                    if (item.optString("type") == "text") {
                        val txt = item.optString("text")
                        if (txt.isNotBlank()) return txt
                    }
                }
            }
            return "AI 未回傳文字內容"
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

    /**
     * 免 API 金鑰／免額度的本機智慧分類整理計畫
     */
    fun offlineSmartOrganize(files: List<VfsFile>): AiOrganizeResult {
        val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")
        val videoExt = setOf("mp4", "mkv", "avi", "mov", "webm", "flv")
        val audioExt = setOf("mp3", "flac", "wav", "aac", "ogg", "m4a")
        val docExt = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv")
        val apkExt = setOf("apk", "apks", "xapk")
        val archiveExt = setOf("zip", "7z", "rar", "tar", "gz", "tgz")

        val onlyFiles = files.filter { !it.isDirectory }
        val moves = onlyFiles.map { f ->
            val ext = f.name.substringAfterLast('.', "").lowercase()
            val target = when (ext) {
                in imageExt -> "圖片"
                in videoExt -> "影片"
                in audioExt -> "音訊"
                in docExt -> "文件"
                in apkExt -> "安裝套件"
                in archiveExt -> "壓縮檔"
                else -> "其他檔案"
            }
            AiMoveInstruction(fileName = f.name, targetFolder = target)
        }
        val groupedCount = moves.groupingBy { it.targetFolder }.eachCount()
        val summary = if (moves.isEmpty()) {
            "目前資料夾內沒有可分類的檔案（共有 ${files.size} 個資料夾）。"
        } else {
            val detail = groupedCount.entries.joinToString("\n") { "• ${it.key}：${it.value} 個檔案" }
            "已完成本機智慧分類（共 ${moves.size} 個檔案）：\n$detail"
        }
        return AiOrganizeResult(summary = summary, moves = moves)
    }
}
