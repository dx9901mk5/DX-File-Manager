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

data class DxSettingsState(
    val recycleBinEnabled: Boolean = true,
    val useShizukuInstall: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val darkTheme: Boolean = true,
    val bookmarks: List<String> = emptyList(),
    val cloudMounts: List<VfsMountConfig> = emptyList()
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val RECYCLE_BIN = booleanPreferencesKey("recycle_bin_enabled")
        val SHIZUKU_INSTALL = booleanPreferencesKey("use_shizuku_install")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden_files")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val BOOKMARKS = stringSetPreferencesKey("bookmarks")
        val CLOUD_MOUNTS_JSON = stringPreferencesKey("cloud_mounts_json")
    }

    private val defaultDownloadPath: String
        get() = File(Environment.getExternalStorageDirectory(), "Download").absolutePath

    val settingsFlow: Flow<DxSettingsState> = context.dxDataStore.data.map { prefs ->
        DxSettingsState(
            recycleBinEnabled = prefs[Keys.RECYCLE_BIN] ?: true,
            useShizukuInstall = prefs[Keys.SHIZUKU_INSTALL] ?: false,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN] ?: false,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            bookmarks = (prefs[Keys.BOOKMARKS] ?: setOf(defaultDownloadPath)).toList().sorted(),
            cloudMounts = decodeMounts(prefs[Keys.CLOUD_MOUNTS_JSON])
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

    suspend fun addBookmark(path: String) {
        context.dxDataStore.edit { prefs ->
            val current = prefs[Keys.BOOKMARKS] ?: setOf(defaultDownloadPath)
            prefs[Keys.BOOKMARKS] = current + path
        }
    }

    suspend fun removeBookmark(path: String) {
        context.dxDataStore.edit { prefs ->
            val current = prefs[Keys.BOOKMARKS] ?: emptySet()
            prefs[Keys.BOOKMARKS] = current - path
        }
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
