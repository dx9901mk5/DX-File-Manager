package com.dx9901mk5.filemanager.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dx9901mk5.filemanager.DxFileManagerApp
import com.dx9901mk5.filemanager.core.archive.ArchiveAndCryptoManager
import com.dx9901mk5.filemanager.core.settings.DxSettingsState
import com.dx9901mk5.filemanager.core.shizuku.InstalledAppInfo
import com.dx9901mk5.filemanager.core.shizuku.ShizukuApkInstaller
import com.dx9901mk5.filemanager.core.trash.TrashItemEntity
import com.dx9901mk5.filemanager.core.vfs.FileCategory
import com.dx9901mk5.filemanager.core.vfs.StorageSpaceInfo
import com.dx9901mk5.filemanager.core.vfs.VfsFile
import com.dx9901mk5.filemanager.core.vfs.VfsMountConfig
import com.dx9901mk5.filemanager.core.vfs.VfsProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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

data class ExplorerPaneState(
    val paneIndex: Int,
    val mode: PaneMode = PaneMode.FILES,
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
    val clipboard = MutableStateFlow<ClipboardState?>(null)
    val installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
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
        viewModelScope.launch {
            var successCount = 0
            files.forEach { f ->
                val res = vfsRegistry.transferBetweenMounts(sourceMountId, f, targetMountId, targetDirPath, isMove)
                if (res.isSuccess) successCount++
            }
            toast("${if (isMove) "移動" else "複製"}完成：$successCount / ${files.size}")
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
        if (localFile.extension.equals("apk", ignoreCase = true) && settings.value.useShizukuInstall) {
            installApkWithShizukuOrFallback(localFile)
            return
        }
        openWithSystemChooser(localFile, file.mimeType)
    }

    fun installApkWithShizukuOrFallback(apkFile: File) {
        viewModelScope.launch {
            if (settings.value.useShizukuInstall && ShizukuApkInstaller.isShizukuReady()) {
                toast("正在透過 Shizuku 靜默安裝 ${apkFile.name}...")
                ShizukuApkInstaller.installApkSilently(apkFile)
                    .onSuccess { toast("靜默安裝成功！") }
                    .onFailure {
                        toast("Shizuku 安裝失敗，改用系統安裝器：${it.message}")
                        openWithSystemChooser(apkFile, "application/vnd.android.package-archive")
                    }
            } else {
                openWithSystemChooser(apkFile, "application/vnd.android.package-archive")
            }
        }
    }

    fun openWithSystemChooser(localFile: File, mimeType: String) {
        runCatching {
            val uri: Uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", localFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "開啟方式").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(chooser)
        }.onFailure {
            toast("找不到可開啟此檔案的應用程式")
        }
    }

    fun createHomeScreenShortcut(file: VfsFile) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(app)) {
            toast("目前桌面不支援釘選捷徑")
            return
        }
        val intent = Intent(app, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("shortcut_path", file.path)
        }
        val shortcut = ShortcutInfoCompat.Builder(app, "dx_shortcut_${file.path.hashCode()}")
            .setShortLabel(file.name)
            .setIcon(IconCompat.createWithResource(app, android.R.drawable.sym_def_app_icon))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(app, shortcut, null)
        toast("已請求建立桌面捷徑：${file.name}")
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

    fun setRecycleBinEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setRecycleBinEnabled(enabled) }
    fun setUseShizukuInstall(enabled: Boolean) = viewModelScope.launch { prefs.setUseShizukuInstall(enabled) }
    fun setShowHiddenFiles(show: Boolean) = viewModelScope.launch {
        prefs.setShowHiddenFiles(show)
        loadPane(2)
        loadPane(3)
    }
    fun setDarkTheme(dark: Boolean) = viewModelScope.launch { prefs.setDarkTheme(dark) }
    fun addBookmark(path: String) = viewModelScope.launch { prefs.addBookmark(path); toast("已加入書籤") }

    private fun toast(msg: String) {
        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
    }
}
