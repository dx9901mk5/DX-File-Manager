package com.dx9901mk5.filemanager.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.util.Locale
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import com.dx9901mk5.filemanager.core.shortcut.ShortcutHelper
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dx9901mk5.filemanager.DxFileManagerApp
import com.dx9901mk5.filemanager.MainActivity
import com.dx9901mk5.filemanager.core.ai.AiMoveInstruction
import com.dx9901mk5.filemanager.core.archive.ArchiveAndCryptoManager
import com.dx9901mk5.filemanager.core.notifications.TransferNotificationHelper
import com.dx9901mk5.filemanager.core.settings.AiProvider
import com.dx9901mk5.filemanager.core.settings.AppLanguage
import com.dx9901mk5.filemanager.core.settings.DarkBackgroundStyle
import com.dx9901mk5.filemanager.core.settings.DxSettingsState
import com.dx9901mk5.filemanager.core.shizuku.InstalledAppInfo
import com.dx9901mk5.filemanager.core.shizuku.ShizukuApkInstaller
import com.dx9901mk5.filemanager.core.vfs.ExternalStorageHelper
import com.dx9901mk5.filemanager.core.vfs.ExternalStorageVolume
import com.dx9901mk5.filemanager.core.trash.TrashItemEntity
import com.dx9901mk5.filemanager.core.vfs.FileCategory
import com.dx9901mk5.filemanager.core.vfs.StorageSpaceInfo
import com.dx9901mk5.filemanager.core.vfs.VfsFile
import com.dx9901mk5.filemanager.core.vfs.VfsMountConfig
import com.dx9901mk5.filemanager.core.vfs.VfsProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class PaneMode {
    FILES,
    CATEGORY,
    APPS,
    TRASH
}

enum class FileViewMode {
    LIST,
    SMALL_GRID,
    LARGE_GRID
}

data class ExplorerPaneState(
    val paneIndex: Int,
    val mode: PaneMode = PaneMode.FILES,
    val viewMode: FileViewMode = FileViewMode.LIST,
    val mountId: String = "local",
    val mountName: String = "內部儲存空間",
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val activeCategory: FileCategory? = null,
    val files: List<VfsFile> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val backStack: List<String> = emptyList(),
    val forwardStack: List<String> = emptyList(),
    val iconScale: Float = 1f
)

data class ClipboardState(
    val mountId: String,
    val files: List<VfsFile>,
    val isCut: Boolean
)

data class OpenWithCandidate(
    val packageName: String,
    val activityName: String,
    val appLabel: String
)

data class OpenWithPrompt(
    val file: File,
    val mimeType: String,
    val defaultKey: String,
    val currentDefaultPkg: String?,
    val candidates: List<OpenWithCandidate>
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DxFileManagerApp
    private val prefs = app.preferences
    private val vfsRegistry = app.vfsRegistry
    private val trashManager = app.trashManager

    val settings: StateFlow<DxSettingsState> = prefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, DxSettingsState())

    val trashItems: StateFlow<List<TrashItemEntity>> = trashManager.observeTrashItems()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val rootPath = Environment.getExternalStorageDirectory().absolutePath
    private val downloadPath = File(rootPath, "Download").absolutePath

    val pane2State = MutableStateFlow(
        ExplorerPaneState(paneIndex = 2, currentPath = rootPath)
    )
    val pane3State = MutableStateFlow(
        ExplorerPaneState(paneIndex = 3, currentPath = if (File(downloadPath).exists()) downloadPath else rootPath)
    )

    val focusedPaneIndex = MutableStateFlow(2)
    val localStorageSpace = MutableStateFlow<StorageSpaceInfo?>(null)
    val externalStorages = MutableStateFlow<List<ExternalStorageVolume>>(emptyList())
    val clipboard = MutableStateFlow<ClipboardState?>(null)
    val installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val openWithPrompt = MutableStateFlow<OpenWithPrompt?>(null)
    private val defaultAppPrefs = app.getSharedPreferences("dx_default_open_apps", Context.MODE_PRIVATE)
    val includeSystemApps = MutableStateFlow(false)
    val selectedAppPackages = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            settings.collect { st ->
                st.cloudMounts.forEach { vfsRegistry.registerMount(it) }
            }
        }
        refreshStorageSpace()
        loadPane(2)
        loadPane(3)
    }

    fun getPaneFlow(paneIndex: Int): MutableStateFlow<ExplorerPaneState> =
        if (paneIndex == 2) pane2State else pane3State

    fun refreshStorageSpace() {
        viewModelScope.launch {
            localStorageSpace.value = vfsRegistry.localProvider.getStorageSpace()
            externalStorages.value = ExternalStorageHelper.listRemovableStorages(app)
        }
    }

    fun loadPane(paneIndex: Int) {
        val flow = getPaneFlow(paneIndex)
        val current = flow.value
        viewModelScope.launch {
            flow.update { it.copy(isLoading = true, errorMessage = null) }
            when (current.mode) {
                PaneMode.FILES -> {
                    val provider = vfsRegistry.getProvider(current.mountId)
                    val res = provider.listFiles(current.currentPath, settings.value.showHiddenFiles)
                    res.onSuccess { list ->
                        flow.update { it.copy(files = list, isLoading = false, selectedPaths = emptySet(), isMultiSelectMode = false) }
                    }.onFailure { err ->
                        flow.update { it.copy(isLoading = false, errorMessage = err.message) }
                    }
                }
                PaneMode.CATEGORY -> {
                    val cat = current.activeCategory ?: FileCategory.RECENT
                    val list = vfsRegistry.localProvider.listByCategory(cat, settings.value.showHiddenFiles)
                    flow.update { it.copy(files = list, isLoading = false, selectedPaths = emptySet()) }
                }
                PaneMode.APPS -> {
                    val apps = ShizukuApkInstaller.listInstalledApps(app, includeSystemApps.value)
                    installedApps.value = apps
                    flow.update { it.copy(isLoading = false) }
                }
                PaneMode.TRASH -> {
                    flow.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun navigateToPath(paneIndex: Int, path: String, mountId: String = getPaneFlow(paneIndex).value.mountId, mountName: String = getPaneFlow(paneIndex).value.mountName) {
        val flow = getPaneFlow(paneIndex)
        val prev = flow.value
        flow.update {
            it.copy(
                mode = PaneMode.FILES,
                mountId = mountId,
                mountName = mountName,
                currentPath = path,
                backStack = if (prev.mode == PaneMode.FILES && prev.currentPath != path) prev.backStack + prev.currentPath else prev.backStack,
                forwardStack = emptyList()
            )
        }
        loadPane(paneIndex)
    }

    fun canNavigateUp(paneIndex: Int): Boolean {
        val cur = getPaneFlow(paneIndex).value
        if (cur.mode != PaneMode.FILES) return true
        if (cur.mountId == "local" && (cur.currentPath == rootPath || cur.currentPath.isBlank())) return false
        if (cur.mountId != "local" && (cur.currentPath == "/" || cur.currentPath.isBlank())) return false
        return File(cur.currentPath).parent != null
    }

    fun navigateUp(paneIndex: Int) {
        val flow = getPaneFlow(paneIndex)
        val cur = flow.value
        if (cur.mode != PaneMode.FILES) {
            navigateToPath(paneIndex, rootPath, "local", "內部儲存空間")
            return
        }
        val parent = File(cur.currentPath).parent ?: return
        if (cur.mountId == "local" && cur.currentPath == rootPath) return
        navigateToPath(paneIndex, parent, cur.mountId, cur.mountName)
    }

    fun navigateBack(paneIndex: Int) {
        val flow = getPaneFlow(paneIndex)
        val cur = flow.value
        val prevPath = cur.backStack.lastOrNull() ?: return
        flow.update {
            it.copy(
                mode = PaneMode.FILES,
                currentPath = prevPath,
                backStack = it.backStack.dropLast(1),
                forwardStack = it.forwardStack + cur.currentPath
            )
        }
        loadPane(paneIndex)
    }

    fun navigateForward(paneIndex: Int) {
        val flow = getPaneFlow(paneIndex)
        val cur = flow.value
        val nextPath = cur.forwardStack.lastOrNull() ?: return
        flow.update {
            it.copy(
                mode = PaneMode.FILES,
                currentPath = nextPath,
                backStack = it.backStack + cur.currentPath,
                forwardStack = it.forwardStack.dropLast(1)
            )
        }
        loadPane(paneIndex)
    }

    fun openCategory(paneIndex: Int, category: FileCategory) {
        val flow = getPaneFlow(paneIndex)
        val newMode = when (category) {
            FileCategory.APPS -> PaneMode.APPS
            FileCategory.TRASH -> PaneMode.TRASH
            else -> PaneMode.CATEGORY
        }
        flow.update {
            it.copy(mode = newMode, activeCategory = category, mountId = "local", mountName = category.title)
        }
        loadPane(paneIndex)
    }

    fun openCloudMount(paneIndex: Int, config: VfsMountConfig) {
        vfsRegistry.registerMount(config)
        navigateToPath(paneIndex, config.rootPath, config.id, config.name)
    }

    fun updateSearchQuery(paneIndex: Int, query: String) {
        getPaneFlow(paneIndex).update { it.copy(searchQuery = query) }
    }

    fun adjustIconScale(paneIndex: Int, delta: Float) {
        getPaneFlow(paneIndex).update {
            it.copy(iconScale = (it.iconScale + delta * 0.1f).coerceIn(0.75f, 1.8f))
        }
    }

    fun setViewMode(paneIndex: Int, mode: FileViewMode) {
        getPaneFlow(paneIndex).update { it.copy(viewMode = mode) }
    }

    fun setPaneExpanded(isExpandedScreen: Boolean, paneIndex: Int, expanded: Boolean) {
        viewModelScope.launch {
            prefs.setPaneExpanded(isExpandedScreen, paneIndex, expanded)
        }
    }

    fun toggleSelection(paneIndex: Int, path: String) {
        getPaneFlow(paneIndex).update { state ->
            val updated = if (path in state.selectedPaths) state.selectedPaths - path else state.selectedPaths + path
            state.copy(selectedPaths = updated, isMultiSelectMode = updated.isNotEmpty())
        }
    }

    fun setSelection(paneIndex: Int, paths: Set<String>, multiSelect: Boolean = paths.isNotEmpty()) {
        getPaneFlow(paneIndex).update { it.copy(selectedPaths = paths, isMultiSelectMode = multiSelect) }
    }

    fun selectAll(paneIndex: Int) {
        getPaneFlow(paneIndex).update { state ->
            state.copy(selectedPaths = state.files.map { it.path }.toSet(), isMultiSelectMode = true)
        }
    }

    fun clearSelection(paneIndex: Int) {
        getPaneFlow(paneIndex).update { it.copy(selectedPaths = emptySet(), isMultiSelectMode = false) }
    }

    fun copyToClipboard(paneIndex: Int, files: List<VfsFile>, isCut: Boolean) {
        val mountId = getPaneFlow(paneIndex).value.mountId
        clipboard.value = ClipboardState(mountId = mountId, files = files, isCut = isCut)
        toast(if (isCut) "已剪下 ${files.size} 個項目" else "已複製 ${files.size} 個項目")
    }

    fun pasteClipboard(targetPaneIndex: Int) {
        val clip = clipboard.value ?: return
        val targetState = getPaneFlow(targetPaneIndex).value
        performTransfer(clip.mountId, clip.files, targetState.mountId, targetState.currentPath, clip.isCut)
        if (clip.isCut) clipboard.value = null
    }

    fun performTransfer(
        sourceMountId: String,
        files: List<VfsFile>,
        targetMountId: String,
        targetDirPath: String,
        isMove: Boolean
    ) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            val notifier = TransferNotificationHelper(app)
            val notificationId = TransferNotificationHelper.allocateNotificationId()
            val isUpload = targetMountId != "local"
            val isDownload = sourceMountId != "local" && targetMountId == "local"
            val actionTitle = when {
                isUpload -> "正在上傳檔案"
                isDownload -> "正在下載檔案"
                isMove -> "正在移動檔案"
                else -> "正在複製檔案"
            }
            val doneTitle = when {
                isUpload -> "上傳完成"
                isDownload -> "下載完成"
                isMove -> "移動完成"
                else -> "複製完成"
            }

            val totalCount = files.size
            val totalBytes = files.sumOf { if (it.isDirectory) 0L else it.size }
            var transferredBytes = 0L
            var lastNotifyTime = 0L
            var successCount = 0

            notifier.showProgress(
                notificationId = notificationId,
                title = actionTitle,
                currentFileName = files.first().name,
                currentIndex = 0,
                totalCount = totalCount,
                bytesTransferred = 0L,
                totalBytes = totalBytes,
                isUpload = isUpload
            )

            files.forEachIndexed { idx, f ->
                val currentIndex = idx + 1
                notifier.showProgress(
                    notificationId = notificationId,
                    title = actionTitle,
                    currentFileName = f.name,
                    currentIndex = currentIndex,
                    totalCount = totalCount,
                    bytesTransferred = transferredBytes,
                    totalBytes = totalBytes,
                    isUpload = isUpload
                )
                val res = vfsRegistry.transferBetweenMounts(
                    sourceMountId = sourceMountId,
                    sourceFile = f,
                    targetMountId = targetMountId,
                    targetDirPath = targetDirPath,
                    isMove = isMove
                ) { delta, activeName ->
                    transferredBytes += delta
                    val now = System.currentTimeMillis()
                    if (now - lastNotifyTime >= 250L) {
                        lastNotifyTime = now
                        notifier.showProgress(
                            notificationId = notificationId,
                            title = actionTitle,
                            currentFileName = activeName,
                            currentIndex = currentIndex,
                            totalCount = totalCount,
                            bytesTransferred = transferredBytes,
                            totalBytes = totalBytes,
                            isUpload = isUpload
                        )
                    }
                }
                if (res.isSuccess) successCount++
            }

            val summary = "$doneTitle：$successCount / $totalCount 個項目"
            notifier.showCompleted(
                notificationId = notificationId,
                title = doneTitle,
                summary = summary,
                isSuccess = successCount == totalCount
            )
            toast(summary)
            loadPane(2)
            loadPane(3)
            refreshStorageSpace()
        }
    }

    fun createFolder(paneIndex: Int, name: String) {
        val state = getPaneFlow(paneIndex).value
        viewModelScope.launch {
            vfsRegistry.getProvider(state.mountId).createDirectory(state.currentPath, name)
                .onSuccess { loadPane(paneIndex) }
                .onFailure { toast("建立失敗：${it.message}") }
        }
    }

    fun renameFiles(paneIndex: Int, files: List<VfsFile>, newNameOrPrefix: String) {
        val state = getPaneFlow(paneIndex).value
        val provider = vfsRegistry.getProvider(state.mountId)
        viewModelScope.launch {
            if (files.size == 1) {
                provider.rename(files.first().path, newNameOrPrefix)
            } else {
                files.forEachIndexed { idx, f ->
                    val ext = if (!f.isDirectory && '.' in f.name) ".${f.name.substringAfterLast('.')}" else ""
                    provider.rename(f.path, "${newNameOrPrefix}_${idx + 1}$ext")
                }
            }
            loadPane(paneIndex)
        }
    }

    fun deleteFiles(paneIndex: Int, files: List<VfsFile>, forcePermanent: Boolean = false) {
        val state = getPaneFlow(paneIndex).value
        val useTrash = settings.value.recycleBinEnabled && !forcePermanent && state.mountId == "local"
        viewModelScope.launch {
            files.forEach { f ->
                if (useTrash) {
                    trashManager.moveToTrash(File(f.path))
                } else {
                    vfsRegistry.getProvider(state.mountId).delete(f.path)
                }
            }
            toast(if (useTrash) "已移至垃圾桶" else "已永久刪除")
            loadPane(paneIndex)
            refreshStorageSpace()
        }
    }

    fun restoreTrashItem(item: TrashItemEntity) {
        viewModelScope.launch {
            trashManager.restoreFromTrash(item)
                .onSuccess { toast("已還原 ${item.originalName}") }
                .onFailure { toast("還原失敗：${it.message}") }
            loadPane(2)
            loadPane(3)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            trashManager.emptyTrash()
            toast("已清空垃圾桶")
        }
    }

    fun compressFiles(paneIndex: Int, files: List<VfsFile>, password: String?) {
        val state = getPaneFlow(paneIndex).value
        if (state.mountId != "local" || files.isEmpty()) return
        viewModelScope.launch {
            val outZip = File(state.currentPath, "${files.first().name.substringBeforeLast('.')}.zip")
            ArchiveAndCryptoManager.compressToZip(files.map { File(it.path) }, outZip, password)
                .onSuccess { toast("壓縮完成：${it.name}"); loadPane(paneIndex) }
                .onFailure { toast("壓縮失敗：${it.message}") }
        }
    }

    fun extractArchive(paneIndex: Int, file: VfsFile, password: String?) {
        val state = getPaneFlow(paneIndex).value
        if (state.mountId != "local") return
        viewModelScope.launch {
            val destDir = File(state.currentPath, file.name.substringBeforeLast('.'))
            ArchiveAndCryptoManager.extractArchive(File(file.path), destDir, password)
                .onSuccess { toast("解壓縮完成：${destDir.name}"); loadPane(paneIndex) }
                .onFailure { toast("解壓縮失敗：${it.message}") }
        }
    }

    fun encryptOrDecryptDxe(paneIndex: Int, file: VfsFile, password: String) {
        if (file.protocol != VfsProtocol.LOCAL || file.isDirectory) return
        viewModelScope.launch {
            val f = File(file.path)
            val res = if (file.name.lowercase().endsWith(".dxe")) {
                ArchiveAndCryptoManager.decryptFileDxe(f, password)
            } else {
                ArchiveAndCryptoManager.encryptFileDxe(f, password)
            }
            res.onSuccess { toast("完成：${it.name}"); loadPane(paneIndex) }
                .onFailure { toast("加解密失敗：${it.message}") }
        }
    }

    fun openFile(file: VfsFile) {
        if (file.protocol != VfsProtocol.LOCAL) {
            toast("請先複製到本機再開啟")
            return
        }
        val localFile = File(file.path)
        if (localFile.extension.equals("apk", ignoreCase = true) &&
            settings.value.useShizukuInstall &&
            ShizukuApkInstaller.isShizukuPluginInstalled(app)
        ) {
            installApkWithShizukuOrFallback(localFile)
            return
        }
        openWithSystemChooser(localFile, file.mimeType)
    }

    fun installApksWithShizuku(apkFiles: List<File>) {
        if (apkFiles.isEmpty()) return
        ShizukuApkInstaller.installApksViaPlugin(app, apkFiles)
            .onFailure { toast(it.message ?: "無法啟動 DX Shizuku 安裝助手") }
    }

    fun installApkWithShizukuOrFallback(apkFile: File) {
        if (settings.value.useShizukuInstall && ShizukuApkInstaller.isShizukuPluginInstalled(app)) {
            ShizukuApkInstaller.installApksViaPlugin(app, listOf(apkFile))
                .onFailure {
                    toast("外掛安裝失敗，改用系統安裝器：${it.message}")
                    openWithSystemChooser(apkFile, "application/vnd.android.package-archive")
                }
        } else {
            openWithSystemChooser(apkFile, "application/vnd.android.package-archive")
        }
    }

    private fun resolveOpenMimeType(localFile: File, hintMime: String): String {
        val ext = localFile.extension.lowercase(Locale.ROOT)
        if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
            when (ext) {
                "mkv", "avi", "mov", "wmv", "flv", "rmvb", "ts", "m2ts", "webm", "3gp", "mp4" -> return "video/*"
                "mp3", "flac", "wav", "aac", "ogg", "m4a", "opus", "ape" -> return "audio/*"
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" -> return "image/*"
                "apk" -> return "application/vnd.android.package-archive"
                "pdf" -> return "application/pdf"
            }
        }
        return hintMime.takeIf { it.isNotBlank() && it != "*/*" } ?: "*/*"
    }

    private fun defaultPreferenceKey(localFile: File, mimeType: String): String {
        val m = mimeType.lowercase(Locale.ROOT)
        return when {
            m.startsWith("video/") -> "cat:video/*"
            m.startsWith("audio/") -> "cat:audio/*"
            m.startsWith("image/") -> "cat:image/*"
            localFile.extension.isNotBlank() -> "ext:${localFile.extension.lowercase(Locale.ROOT)}"
            else -> "mime:$m"
        }
    }

    fun openWithSystemChooser(localFile: File, mimeType: String, forceAsk: Boolean = false) {
        val resolvedMime = resolveOpenMimeType(localFile, mimeType)
        val prefKey = defaultPreferenceKey(localFile, resolvedMime)
        val savedPkg = defaultAppPrefs.getString(prefKey, null)

        if (!forceAsk && !savedPkg.isNullOrBlank()) {
            val launched = launchWithTarget(localFile, resolvedMime, savedPkg, activityName = null)
            if (launched) return
            defaultAppPrefs.edit().remove(prefKey).apply()
        }

        val uri = runCatching {
            FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", localFile)
        }.getOrNull()
        if (uri == null) {
            toast("無法存取該檔案")
            return
        }

        val queryIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pm = app.packageManager
        val resolvedList = runCatching {
            pm.queryIntentActivities(queryIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())

        val candidates = resolvedList
            .filter { it.activityInfo.packageName != app.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                OpenWithCandidate(
                    packageName = info.activityInfo.packageName,
                    activityName = info.activityInfo.name,
                    appLabel = info.loadLabel(pm).toString().ifBlank { info.activityInfo.packageName }
                )
            }

        if (candidates.isNotEmpty()) {
            if (!forceAsk && candidates.size == 1) {
                val only = candidates.first()
                if (launchWithTarget(localFile, resolvedMime, only.packageName, only.activityName)) return
            }
            openWithPrompt.value = OpenWithPrompt(
                file = localFile,
                mimeType = resolvedMime,
                defaultKey = prefKey,
                currentDefaultPkg = savedPkg,
                candidates = candidates
            )
            return
        }

        // 若系統未回傳候選清單，直接送出 ACTION_VIEW（不包 createChooser，讓系統原生彈窗保留「一律採用」）
        launchSystemDirectView(uri, resolvedMime)
    }

    fun launchOpenWithCandidate(
        prompt: OpenWithPrompt,
        candidate: OpenWithCandidate,
        setAsDefault: Boolean
    ) {
        if (setAsDefault) {
            defaultAppPrefs.edit().putString(prompt.defaultKey, candidate.packageName).apply()
        }
        openWithPrompt.value = null
        if (!launchWithTarget(prompt.file, prompt.mimeType, candidate.packageName, candidate.activityName)) {
            toast("無法使用 ${candidate.appLabel} 開啟此檔案")
        }
    }

    fun launchSystemOpenChooser(prompt: OpenWithPrompt) {
        openWithPrompt.value = null
        runCatching {
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", prompt.file)
            launchSystemDirectView(uri, prompt.mimeType)
        }.onFailure {
            toast("找不到可開啟此檔案的應用程式")
        }
    }

    fun dismissOpenWithPrompt() {
        openWithPrompt.value = null
    }

    fun clearDefaultOpenApp(localFile: File, mimeType: String) {
        val resolvedMime = resolveOpenMimeType(localFile, mimeType)
        val prefKey = defaultPreferenceKey(localFile, resolvedMime)
        defaultAppPrefs.edit().remove(prefKey).apply()
        toast("已重置此類檔案的預設開啟程式（$resolvedMime）")
    }

    fun clearAllDefaultOpenApps() {
        defaultAppPrefs.edit().clear().apply()
        toast("已清除所有預設開啟應用程式記憶")
    }

    private fun launchWithTarget(
        localFile: File,
        mimeType: String,
        packageName: String,
        activityName: String?
    ): Boolean {
        return runCatching {
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", localFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!activityName.isNullOrBlank()) {
                    component = ComponentName(packageName, activityName)
                } else {
                    setPackage(packageName)
                }
            }
            app.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun launchSystemDirectView(uri: Uri, mimeType: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        }.onFailure {
            toast("找不到可開啟此檔案的應用程式")
        }
    }

    fun createHomeScreenShortcut(file: VfsFile) {
        ShortcutHelper.pinFilesToDesktop(app, listOf(file)) {
            toast("目前桌面不支援釘選捷徑")
        }
    }

    fun exportSelectedApps(packageNames: List<String>) {
        viewModelScope.launch {
            var count = 0
            packageNames.forEach { pkg ->
                if (ShizukuApkInstaller.exportInstalledAppApk(app, pkg).isSuccess) count++
            }
            selectedAppPackages.value = emptySet()
            toast("已匯出 $count 個 APK 至 Download/DX_Backup_APK")
            loadPane(2)
            loadPane(3)
        }
    }

    fun toggleAppPackageSelection(pkg: String) {
        selectedAppPackages.update { cur -> if (pkg in cur) cur - pkg else cur + pkg }
    }

    fun toggleSystemApps() {
        includeSystemApps.update { !it }
        if (pane2State.value.mode == PaneMode.APPS) loadPane(2)
        if (pane3State.value.mode == PaneMode.APPS) loadPane(3)
    }

    fun saveCloudMount(config: VfsMountConfig) {
        viewModelScope.launch {
            prefs.saveCloudMount(config)
            vfsRegistry.registerMount(config)
            toast("已掛載 ${config.name}")
        }
    }

    fun removeCloudMount(mountId: String) {
        viewModelScope.launch {
            prefs.removeCloudMount(mountId)
            vfsRegistry.removeMount(mountId)
            toast("已移除掛載")
        }
    }

    fun setRecycleBinEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setRecycleBinEnabled(enabled) }
    fun setUseShizukuInstall(enabled: Boolean) = viewModelScope.launch { prefs.setUseShizukuInstall(enabled) }
    fun setShowHiddenFiles(show: Boolean) = viewModelScope.launch {
        prefs.setShowHiddenFiles(show)
        loadPane(2)
        loadPane(3)
    }
    fun setDarkTheme(dark: Boolean) = viewModelScope.launch { prefs.setDarkTheme(dark) }
    fun setDarkBackgroundStyle(style: DarkBackgroundStyle) = viewModelScope.launch { prefs.setDarkBackgroundStyle(style) }
    fun setLanguage(language: AppLanguage) = viewModelScope.launch { prefs.setLanguage(language) }
    fun addBookmark(path: String) = viewModelScope.launch { prefs.addBookmark(path); toast("已加入書籤") }
    fun removeBookmark(path: String) = viewModelScope.launch { prefs.removeBookmark(path); toast("已移除書籤") }
    fun setAiProvider(provider: AiProvider) = viewModelScope.launch { prefs.setAiProvider(provider) }
    fun setAiApiKey(provider: AiProvider, key: String) = viewModelScope.launch { prefs.setAiApiKey(provider, key) }
    fun setAiModel(provider: AiProvider, model: String) = viewModelScope.launch { prefs.setAiModel(provider, model) }
    fun setAiThinkingEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setAiThinkingEnabled(enabled) }

    suspend fun organizeFilesWithPlan(paneIndex: Int, moves: List<AiMoveInstruction>): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val state = if (paneIndex == 3) pane3State.value else pane2State.value
                val provider = vfsRegistry.getProvider(state.mountId)
                val byName = state.files.associateBy { it.name }
                var movedCount = 0
                val createdFolders = mutableSetOf<String>()

                for (mv in moves) {
                    val src = byName[mv.fileName] ?: continue
                    val safeFolder = mv.targetFolder.replace("/", "_").replace("\\", "_").trim()
                    if (safeFolder.isEmpty()) continue
                    val targetDirPath = state.currentPath.trimEnd('/') + "/" + safeFolder
                    if (createdFolders.add(targetDirPath)) {
                        provider.createDirectory(state.currentPath, safeFolder)
                    }
                    if (provider.move(src.path, targetDirPath).isSuccess) {
                        movedCount++
                    }
                }
                loadPane(paneIndex)
                movedCount
            }
        }

    private fun toast(msg: String) {
        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
    }
}
