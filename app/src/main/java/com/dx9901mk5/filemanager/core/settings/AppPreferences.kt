package com.dx9901mk5.filemanager.core.settings

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dx9901mk5.filemanager.core.vfs.VfsMountConfig
import com.dx9901mk5.filemanager.core.vfs.VfsProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Context.dxDataStore: DataStore<Preferences> by preferencesDataStore(name = "dx_file_manager_prefs")

enum class DarkBackgroundStyle(val key: String) {
    OLED_BLACK("oled_black"),
    DARK_GRAY("dark_gray");

    companion object {
        fun fromKey(key: String?): DarkBackgroundStyle =
            entries.firstOrNull { it.key == key } ?: OLED_BLACK
    }
}

enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val keyHelpUrl: String
) {
    GEMINI("gemini", "Gemini", "gemini-2.5-flash", "https://aistudio.google.com/apikey"),
    GPT("gpt", "GPT (OpenAI)", "gpt-4o-mini", "https://platform.openai.com/api-keys"),
    CLAUDE("claude", "Claude", "claude-3-5-haiku-latest", "https://console.anthropic.com/settings/keys"),
    GROK("grok", "Grok", "grok-2-latest", "https://console.x.ai/");

    companion object {
        fun fromId(id: String?): AiProvider =
            entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

data class DxSettingsState(
    val recycleBinEnabled: Boolean = true,
    val useShizukuInstall: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val darkTheme: Boolean = true,
    val darkBackgroundStyle: DarkBackgroundStyle = DarkBackgroundStyle.OLED_BLACK,
    val language: AppLanguage = AppLanguage.fromSystemOrCode(null),
    val bookmarks: List<String> = emptyList(),
    val cloudMounts: List<VfsMountConfig> = emptyList(),
    val aiProvider: AiProvider = AiProvider.GEMINI,
    val aiApiKeys: Map<AiProvider, String> = emptyMap(),
    // 記憶各分區上次展開或折疊狀態 (手機直向預設第1+2區、無第3區；平板/摺疊預設三區全開)
    val pane1ExpandedCompact: Boolean = true,
    val pane2ExpandedCompact: Boolean = true,
    val pane3ExpandedCompact: Boolean = false,
    val pane1ExpandedTablet: Boolean = true,
    val pane2ExpandedTablet: Boolean = true,
    val pane3ExpandedTablet: Boolean = true
) {
    val currentAiApiKey: String
        get() = aiApiKeys[aiProvider].orEmpty()
}

class AppPreferences(private val context: Context) {

    private object Keys {
        val RECYCLE_BIN = booleanPreferencesKey("recycle_bin_enabled")
        val SHIZUKU_INSTALL = booleanPreferencesKey("use_shizuku_install")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden_files")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DARK_BG_STYLE = stringPreferencesKey("dark_bg_style")
        val LANGUAGE = stringPreferencesKey("app_language")
        val BOOKMARKS = stringSetPreferencesKey("bookmarks")
        val CLOUD_MOUNTS_JSON = stringPreferencesKey("cloud_mounts_json")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_KEYS_JSON = stringPreferencesKey("ai_keys_json")

        val P1_COMPACT = booleanPreferencesKey("pane1_expanded_compact")
        val P2_COMPACT = booleanPreferencesKey("pane2_expanded_compact")
        val P3_COMPACT = booleanPreferencesKey("pane3_expanded_compact")
        val P1_TABLET = booleanPreferencesKey("pane1_expanded_tablet")
        val P2_TABLET = booleanPreferencesKey("pane2_expanded_tablet")
        val P3_TABLET = booleanPreferencesKey("pane3_expanded_tablet")
    }

    private val defaultDownloadPath: String
        get() = File(Environment.getExternalStorageDirectory(), "Download").absolutePath

    val settingsFlow: Flow<DxSettingsState> = context.dxDataStore.data.map { prefs ->
        DxSettingsState(
            recycleBinEnabled = prefs[Keys.RECYCLE_BIN] ?: true,
            useShizukuInstall = prefs[Keys.SHIZUKU_INSTALL] ?: false,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN] ?: false,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            darkBackgroundStyle = DarkBackgroundStyle.fromKey(prefs[Keys.DARK_BG_STYLE]),
            language = AppLanguage.fromSystemOrCode(prefs[Keys.LANGUAGE]),
            bookmarks = (prefs[Keys.BOOKMARKS] ?: setOf(defaultDownloadPath)).toList().sorted(),
            cloudMounts = decodeMounts(prefs[Keys.CLOUD_MOUNTS_JSON]),
            aiProvider = AiProvider.fromId(prefs[Keys.AI_PROVIDER]),
            aiApiKeys = decodeAiKeys(prefs[Keys.AI_KEYS_JSON]),
            pane1ExpandedCompact = prefs[Keys.P1_COMPACT] ?: true,
            pane2ExpandedCompact = prefs[Keys.P2_COMPACT] ?: true,
            pane3ExpandedCompact = prefs[Keys.P3_COMPACT] ?: false,
            pane1ExpandedTablet = prefs[Keys.P1_TABLET] ?: true,
            pane2ExpandedTablet = prefs[Keys.P2_TABLET] ?: true,
            pane3ExpandedTablet = prefs[Keys.P3_TABLET] ?: true
        )
    }

    suspend fun setRecycleBinEnabled(enabled: Boolean) {
        context.dxDataStore.edit { it[Keys.RECYCLE_BIN] = enabled }
    }

    suspend fun setUseShizukuInstall(enabled: Boolean) {
        context.dxDataStore.edit { it[Keys.SHIZUKU_INSTALL] = enabled }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.dxDataStore.edit { it[Keys.SHOW_HIDDEN] = show }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dxDataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun setDarkBackgroundStyle(style: DarkBackgroundStyle) {
        context.dxDataStore.edit { it[Keys.DARK_BG_STYLE] = style.key }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dxDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setPaneExpanded(isExpandedScreen: Boolean, paneIndex: Int, expanded: Boolean) {
        context.dxDataStore.edit { prefs ->
            val key = when {
                !isExpandedScreen && paneIndex == 1 -> Keys.P1_COMPACT
                !isExpandedScreen && paneIndex == 2 -> Keys.P2_COMPACT
                !isExpandedScreen && paneIndex == 3 -> Keys.P3_COMPACT
                isExpandedScreen && paneIndex == 1 -> Keys.P1_TABLET
                isExpandedScreen && paneIndex == 2 -> Keys.P2_TABLET
                else -> Keys.P3_TABLET
            }
            prefs[key] = expanded
        }
    }

    suspend fun addBookmark(path: String) {
        context.dxDataStore.edit { prefs ->
            val current = prefs[Keys.BOOKMARKS] ?: setOf(defaultDownloadPath)
            prefs[Keys.BOOKMARKS] = current + path
        }
    }

    suspend fun removeBookmark(path: String) {
        context.dxDataStore.edit { prefs ->
            val current = prefs[Keys.BOOKMARKS] ?: setOf(defaultDownloadPath)
            prefs[Keys.BOOKMARKS] = current - path
        }
    }

    suspend fun setAiProvider(provider: AiProvider) {
        context.dxDataStore.edit { it[Keys.AI_PROVIDER] = provider.id }
    }

    suspend fun setAiApiKey(provider: AiProvider, apiKey: String) {
        context.dxDataStore.edit { prefs ->
            val map = decodeAiKeys(prefs[Keys.AI_KEYS_JSON]).toMutableMap()
            if (apiKey.isBlank()) map.remove(provider) else map[provider] = apiKey.trim()
            prefs[Keys.AI_KEYS_JSON] = encodeAiKeys(map)
        }
    }

    private fun encodeAiKeys(keys: Map<AiProvider, String>): String {
        val obj = JSONObject()
        keys.forEach { (p, k) -> obj.put(p.id, k) }
        return obj.toString()
    }

    private fun decodeAiKeys(raw: String?): Map<AiProvider, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                AiProvider.entries.forEach { p ->
                    val v = obj.optString(p.id)
                    if (v.isNotBlank()) put(p, v)
                }
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun saveCloudMount(config: VfsMountConfig) {
        context.dxDataStore.edit { prefs ->
            val list = decodeMounts(prefs[Keys.CLOUD_MOUNTS_JSON]).filterNot { it.id == config.id } + config
            prefs[Keys.CLOUD_MOUNTS_JSON] = encodeMounts(list)
        }
    }

    suspend fun removeCloudMount(mountId: String) {
        context.dxDataStore.edit { prefs ->
            val list = decodeMounts(prefs[Keys.CLOUD_MOUNTS_JSON]).filterNot { it.id == mountId }
            prefs[Keys.CLOUD_MOUNTS_JSON] = encodeMounts(list)
        }
    }

    private fun encodeMounts(mounts: List<VfsMountConfig>): String {
        val arr = JSONArray()
        mounts.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                put("protocol", m.protocol.name)
                put("host", m.host)
                put("port", m.port)
                put("username", m.username)
                put("password", m.password)
                put("shareOrBucket", m.shareOrBucket)
                put("accessToken", m.accessToken)
                put("rootPath", m.rootPath)
            })
        }
        return arr.toString()
    }

    private fun decodeMounts(raw: String?): List<VfsMountConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val proto = VfsProtocol.entries.firstOrNull { it.name == o.optString("protocol") }
                        ?: VfsProtocol.WEBDAV
                    add(
                        VfsMountConfig(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            protocol = proto,
                            host = o.optString("host"),
                            port = o.optInt("port", proto.defaultPort),
                            username = o.optString("username"),
                            password = o.optString("password"),
                            shareOrBucket = o.optString("shareOrBucket"),
                            accessToken = o.optString("accessToken"),
                            rootPath = o.optString("rootPath", "/")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
