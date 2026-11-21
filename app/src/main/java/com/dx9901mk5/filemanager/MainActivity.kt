package com.dx9901mk5.filemanager

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.dx9901mk5.filemanager.core.billing.ProLicenseManager
import com.dx9901mk5.filemanager.core.settings.DarkBackgroundStyle
import com.dx9901mk5.filemanager.core.settings.LocalDxStrings
import com.dx9901mk5.filemanager.core.settings.dxStringsFor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
import com.dx9901mk5.filemanager.ui.gesture.DxHaptics
import com.dx9901mk5.filemanager.ui.gesture.DxSoundEffects
import com.dx9901mk5.filemanager.ui.gesture.SystemCrossAppDragHelper
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.core.vfs.OAuth2AuthHelper
import com.dx9901mk5.filemanager.core.vfs.VfsFile
import com.dx9901mk5.filemanager.ui.FileManagerViewModel
import com.dx9901mk5.filemanager.ui.dialogs.AiFileAssistantDialog
import com.dx9901mk5.filemanager.ui.dialogs.CloudMountWizardDialog
import com.dx9901mk5.filemanager.ui.dialogs.ContextMenuAction
import com.dx9901mk5.filemanager.ui.dialogs.DxStorageAnalyzerDialog
import com.dx9901mk5.filemanager.ui.dialogs.DxWebShareDialog
import com.dx9901mk5.filemanager.ui.dialogs.FileContextMenuDialog
import com.dx9901mk5.filemanager.ui.dialogs.FilePropertiesDialog
import com.dx9901mk5.filemanager.ui.dialogs.NewFolderDialog
import com.dx9901mk5.filemanager.ui.dialogs.PasswordInputDialog
import com.dx9901mk5.filemanager.ui.dialogs.ProUpgradeDialog
import com.dx9901mk5.filemanager.ui.dialogs.RenameDialog
import com.dx9901mk5.filemanager.ui.dialogs.SettingsAndAboutDialog
import com.dx9901mk5.filemanager.ui.dialogs.ShortcutCheatSheetDialog
import com.dx9901mk5.filemanager.ui.gesture.CopyOrMoveDialog
import com.dx9901mk5.filemanager.core.shortcut.ShortcutHelper
import com.dx9901mk5.filemanager.ui.components.FileThumbnailIcon
import com.dx9901mk5.filemanager.ui.components.IosGlassFolderIcon
import com.dx9901mk5.filemanager.ui.gesture.CrossPaneDragController
import com.dx9901mk5.filemanager.ui.gesture.DragAndDropOverlay
import com.dx9901mk5.filemanager.ui.gesture.DxShortcutAction
import com.dx9901mk5.filemanager.ui.gesture.PendingDropRequest
import com.dx9901mk5.filemanager.ui.gesture.dxKeyboardShortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dx9901mk5.filemanager.ui.layout.FileExplorerPane
import com.dx9901mk5.filemanager.ui.layout.NavigationPane
import com.dx9901mk5.filemanager.ui.layout.ThreePaneScaffold
import com.dx9901mk5.filemanager.ui.theme.DxFileManagerTheme
import java.io.File
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private val viewModel: FileManagerViewModel by viewModels()
    private var assignedWindowSlot: Int = 2
    private val activeShortcutGroup = mutableStateOf<Pair<String, List<VfsFile>>?>(null)

    private val showStoragePermissionDialog = mutableStateOf(false)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    private val requestLegacyStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            if (hasStoragePermission()) {
                showStoragePermissionDialog.value = false
                viewModel.refreshStorageSpace()
                viewModel.navigateToPath(2, viewModel.pane2State.value.currentPath)
                viewModel.navigateToPath(3, viewModel.pane3State.value.currentPath)
                ensureNotificationPermission()
            }
        }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ProLicenseManager.init(this)
        if (hasStoragePermission()) {
            ensureNotificationPermission()
        } else {
            showStoragePermissionDialog.value = true
        }
        OAuth2AuthHelper.handleRedirectIntent(intent)

        val preferredSlot = intent.getIntExtra("extra_window_slot", -1).takeIf { it in 2..9 }
        val launchedAsSmallWindow = intent.getBooleanExtra("extra_small_window_mode", false)
        val isMainLauncher = !launchedAsSmallWindow && preferredSlot == null
        assignedWindowSlot = SystemCrossAppDragHelper.allocateWindowSlot(
            context = this,
            taskId = taskId,
            preferredSlot = preferredSlot,
            isMainLauncher = isMainLauncher
        )

        handleShortcutIntent(intent)

        setContent {
            val s = LocalDxStrings.current
    val licenseStatus by ProLicenseManager.status.collectAsState()
    val settings by viewModel.settings.collectAsState()
            val windowSizeClass = calculateWindowSizeClass(this)
            val isExpandedScreen = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
            val isLargeTablet = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
            val isSmallWindowMode = isInMultiWindowMode || launchedAsSmallWindow || assignedWindowSlot > 2

            DxFileManagerTheme(
                darkTheme = settings.darkTheme,
                darkBackgroundStyle = settings.darkBackgroundStyle
            ) {
                CompositionLocalProvider(
                    LocalDxStrings provides dxStringsFor(settings.language),
                    LocalOverscrollConfiguration provides null
                ) {
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                viewModel.refreshStorageSpace()
                                if (hasStoragePermission() && showStoragePermissionDialog.value) {
                                    showStoragePermissionDialog.value = false
                                    viewModel.navigateToPath(2, viewModel.pane2State.value.currentPath)
                                    viewModel.navigateToPath(3, viewModel.pane3State.value.currentPath)
                                    ensureNotificationPermission()
                                }
                            }
                        }
                        val otgReceiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                viewModel.refreshStorageSpace()
                            }
                        }
                        val mediaFilter = IntentFilter().apply {
                            addAction(Intent.ACTION_MEDIA_MOUNTED)
                            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                            addAction(Intent.ACTION_MEDIA_REMOVED)
                            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
                            addAction(Intent.ACTION_MEDIA_EJECT)
                            addDataScheme("file")
                        }
                        val usbFilter = IntentFilter().apply {
                            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                        }
                        ContextCompat.registerReceiver(this@MainActivity, otgReceiver, mediaFilter, ContextCompat.RECEIVER_EXPORTED)
                        ContextCompat.registerReceiver(this@MainActivity, otgReceiver, usbFilter, ContextCompat.RECEIVER_EXPORTED)
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                            runCatching { unregisterReceiver(otgReceiver) }
                        }
                    }
                    val isGithubExpired = remember {
                        !BuildConfig.IS_PLAY_EDITION && isGithubEditionExpired()
                    }
                    if (isGithubExpired) {
                        GithubExpiredBlockingScreen(
                            onOpenPlayStore = {
                                val marketIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=com.dx9901mk5.filemanager")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                val webIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=com.dx9901mk5.filemanager")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { startActivity(marketIntent) }
                                    .onFailure { runCatching { startActivity(webIntent) } }
                            },
                            onExit = { finishAffinity() }
                        )
                    } else {
                        DxFileManagerScreen(
                            viewModel = viewModel,
                            isExpandedScreen = isExpandedScreen,
                            isLargeTablet = isLargeTablet,
                            windowSlot = assignedWindowSlot,
                            isSmallWindowMode = isSmallWindowMode
                        )

                        if (showStoragePermissionDialog.value) {
                            AlertDialog(
                                onDismissRequest = {
                                    showStoragePermissionDialog.value = false
                                    ensureNotificationPermission()
                                },
                                title = { Text("需要檔案管理權限") },
                                text = {
                                    Text("DX 檔案管理器需要「所有檔案存取權」才能瀏覽、搬移、壓縮與管理您裝置中的檔案與資料夾。")
                                },
                                confirmButton = {
                                    Button(onClick = { ensureStoragePermission() }) {
                                        Text("立即授權")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        showStoragePermissionDialog.value = false
                                        ensureNotificationPermission()
                                    }) {
                                        Text("稍後再說")
                                    }
                                }
                            )
                        }

                activeShortcutGroup.value?.let { (groupTitle, groupFiles) ->
                    AlertDialog(
                        onDismissRequest = { activeShortcutGroup.value = null },
                        title = { Text(groupTitle) },
                        text = {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(groupFiles.size) { idx ->
                                    val item = groupFiles[idx]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                activeShortcutGroup.value = null
                                                if (item.isDirectory) {
                                                    viewModel.navigateToPath(2, item.path)
                                                } else {
                                                    File(item.path).parentFile?.let { p ->
                                                        if (p.exists() && p.isDirectory) {
                                                            viewModel.navigateToPath(2, p.absolutePath)
                                                        }
                                                    }
                                                    viewModel.openFile(item)
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (item.isDirectory) {
                                            IosGlassFolderIcon(
                                                isEmpty = item.isEmpty,
                                                accentColor = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        } else {
                                            FileThumbnailIcon(file = item, iconSize = 34.dp)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.path,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { activeShortcutGroup.value = null }) {
                                Text(LocalDxStrings.current.done)
                            }
                        }
                    )
                }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        SystemCrossAppDragHelper.releaseWindowSlot(taskId)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        OAuth2AuthHelper.handleRedirectIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        val groupId = intent?.getStringExtra("shortcut_group_id")
        if (!groupId.isNullOrBlank()) {
            val files = ShortcutHelper.getShortcutGroup(this, groupId)
            val title = intent.getStringExtra("shortcut_group_title") ?: "桌面捷徑資料夾"
            if (files.isNotEmpty()) {
                activeShortcutGroup.value = title to files
            }
            return
        }
        val path = intent?.getStringExtra("shortcut_path") ?: return
        val target = File(path)
        if (!target.exists()) return
        if (target.isDirectory) {
            viewModel.navigateToPath(2, target.absolutePath)
        } else {
            target.parentFile?.let { parent ->
                if (parent.exists() && parent.isDirectory) {
                    viewModel.navigateToPath(2, parent.absolutePath)
                }
            }
            viewModel.openFile(
                VfsFile(
                    name = target.name,
                    path = target.absolutePath,
                    isDirectory = false,
                    size = target.length(),
                    lastModified = target.lastModified(),
                    mountId = "local"
                )
            )
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                com.dx9901mk5.filemanager.ui.dialogs.openSettingsWithScrollHighlight(
                    context = this,
                    action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    fallbackAction = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                )
            }
        } else if (!hasStoragePermission()) {
            requestLegacyStoragePermission.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}

@Composable
private fun DxFileManagerScreen(
    viewModel: FileManagerViewModel,
    isExpandedScreen: Boolean,
    isLargeTablet: Boolean,
    windowSlot: Int = 2,
    isSmallWindowMode: Boolean = false
) {
    val s = LocalDxStrings.current
    val licenseStatus by ProLicenseManager.status.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val pane2State by viewModel.pane2State.collectAsState()
    val pane3State by viewModel.pane3State.collectAsState()
    val focusedPane by viewModel.focusedPaneIndex.collectAsState()
    val storageSpace by viewModel.localStorageSpace.collectAsState()
    val externalStorages by viewModel.externalStorages.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val trashItems by viewModel.trashItems.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val includeSystemApps by viewModel.includeSystemApps.collectAsState()
    val selectedAppPackages by viewModel.selectedAppPackages.collectAsState()
    val openWithPrompt by viewModel.openWithPrompt.collectAsState()

    val dragController = remember { CrossPaneDragController() }
    dragController.onRequestCrossPaneSwitch = { targetPane ->
        // 拖曳到各分區展開按鈕時，自動展開目標分區並摺疊先前分區
        if (targetPane == 2 || targetPane == 3) {
            val prevPane = if (targetPane == 2) 3 else 2
            viewModel.setPaneExpanded(isExpandedScreen, targetPane, true)
            viewModel.setPaneExpanded(isExpandedScreen, prevPane, false)
        } else if (targetPane == 1) {
            viewModel.setPaneExpanded(isExpandedScreen, 1, true)
        }
    }

    var showShortcutsDialog by remember { mutableStateOf(false) }
    var showCloudWizard by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAiAssistantDialog by remember { mutableStateOf(false) }
    var showStorageAnalyzer by remember { mutableStateOf(false) }
    var showWebShare by remember { mutableStateOf(false) }
    var showNewFolderForPane by remember { mutableStateOf<Int?>(null) }
    var showEmptyTrashConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteRequest by remember { mutableStateOf<Triple<Int, List<VfsFile>, Boolean>?>(null) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showFreeformGuideDialog by remember { mutableStateOf(false) }
    var dontShowFreeformGuideAgain by remember { mutableStateOf(false) }
    var showProUpgradeDialog by remember { mutableStateOf(false) }
    var proUpgradeReason by remember { mutableStateOf<String?>(null) }
    // Pair(paneIndex, prioritizeCreateOrCompress)
    var bottomActionBarState by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    var contextMenuTarget by remember { mutableStateOf<Pair<Int, VfsFile?>?>(null) }
    var renameTarget by remember { mutableStateOf<Pair<Int, List<VfsFile>>?>(null) }
    var passwordPrompt by remember { mutableStateOf<Triple<String, String, (String) -> Unit>?>(null) }
    var propertiesTarget by remember { mutableStateOf<VfsFile?>(null) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showPane3 = !isSmallWindowMode
    var isRatioEditMode by remember { mutableStateOf(false) }
    var smallWinPane1Expanded by remember { mutableStateOf(false) }

    // 記憶用戶在原生小視窗模式下調整的寬高比例
    val smallWindowSaveContext = LocalContext.current
    val hostActivity = smallWindowSaveContext as? Activity
    val isSystemMultiWin = hostActivity?.isInMultiWindowMode == true

    LaunchedEffect(isSmallWindowMode, isSystemMultiWin, configuration.screenWidthDp, configuration.screenHeightDp) {
        if (isSmallWindowMode && isSystemMultiWin && configuration.screenWidthDp in 220..640 && configuration.screenHeightDp in 280..760) {
            SystemCrossAppDragHelper.saveSmallWindowSizeDp(
                smallWindowSaveContext,
                configuration.screenWidthDp,
                configuration.screenHeightDp
            )
        }
    }

    val pane1Expanded = if (isSmallWindowMode) {
        smallWinPane1Expanded
    } else if (isExpandedScreen || isLandscape) {
        settings.pane1ExpandedTablet
    } else {
        settings.pane1ExpandedCompact
    }
    val pane2Expanded = if (isSmallWindowMode) {
        true
    } else if (isExpandedScreen || isLandscape) {
        settings.pane2ExpandedTablet
    } else {
        settings.pane2ExpandedCompact
    }
    val pane3Expanded = if (isSmallWindowMode) {
        false
    } else if (isExpandedScreen || isLandscape) {
        settings.pane3ExpandedTablet
    } else {
        settings.pane3ExpandedCompact
    }

    val activityContext = LocalContext.current
    val hapticView = LocalView.current
    val uiScope = rememberCoroutineScope()

    // 接收來自另一個 DX_fileManager 視窗或外部 App 拖曳放開的檔案
    DisposableEffect(hapticView, focusedPane, pane2State.currentPath, pane3State.currentPath, pane2State.mountId, pane3State.mountId) {
        SystemCrossAppDragHelper.attachWindowDropReceiver(
            view = hapticView,
            onDxFilesDropped = { droppedFiles ->
                val targetPane = if (focusedPane == 3 && pane3Expanded) 3 else 2
                val targetState = if (targetPane == 3) pane3State else pane2State
                DxHaptics.lightVibrate(activityContext, hapticView)
                dragController.pendingDropTarget = PendingDropRequest(
                    files = droppedFiles,
                    sourcePaneIndex = targetPane,
                    targetPaneIndex = targetPane,
                    targetMountId = targetState.mountId,
                    targetDirPath = targetState.currentPath
                )
            },
            onExternalUrisDropped = { uris ->
                val targetPane = if (focusedPane == 3 && pane3Expanded) 3 else 2
                val targetState = if (targetPane == 3) pane3State else pane2State
                if (targetState.mountId != "local") return@attachWindowDropReceiver
                val destDir = File(targetState.currentPath)
                if (!destDir.exists() || !destDir.isDirectory) return@attachWindowDropReceiver
                uiScope.launch {
                    val savedCount = withContext(Dispatchers.IO) {
                        var count = 0
                        for (uri in uris) {
                            runCatching {
                                val cr = activityContext.contentResolver
                                var displayName = "dropped_${System.currentTimeMillis()}"
                                cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                                    if (c.moveToFirst()) {
                                        c.getString(0)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                                    }
                                }
                                var outFile = File(destDir, displayName)
                                if (outFile.exists()) {
                                    val dot = displayName.lastIndexOf('.')
                                    val base = if (dot > 0) displayName.substring(0, dot) else displayName
                                    val ext = if (dot > 0) displayName.substring(dot) else ""
                                    outFile = File(destDir, "${base}_${System.currentTimeMillis()}$ext")
                                }
                                cr.openInputStream(uri)?.use { input ->
                                    outFile.outputStream().use { output -> input.copyTo(output) }
                                }
                                count++
                            }
                        }
                        count
                    }
                    if (savedCount > 0) {
                        DxHaptics.lightVibrate(activityContext, hapticView)
                        viewModel.navigateToPath(targetPane, destDir.absolutePath, "local", "內部儲存空間")
                    }
                }
            }
        )
        onDispose { hapticView.setOnDragListener(null) }
    }

    // 拖曳懸停於任一資料夾上方滿 2 秒（顯示放大鏡特效）-> 自動進入該資料夾，放開手指才詢問複製或移動
    LaunchedEffect(dragController.isDragging, dragController.hoveredFolderPaneIndex, dragController.hoveredFolderPath) {
        val paneIdx = dragController.hoveredFolderPaneIndex
        val folderPath = dragController.hoveredFolderPath
        if (dragController.isDragging && (paneIdx == 2 || paneIdx == 3) && folderPath != null) {
            if (dragController.draggedFiles.none { it.path == folderPath }) {
                delay(2000L)
                if (dragController.isDragging &&
                    dragController.hoveredFolderPaneIndex == paneIdx &&
                    dragController.hoveredFolderPath == folderPath
                ) {
                    DxHaptics.lightVibrate(activityContext, hapticView)
                    viewModel.navigateToPath(paneIdx, folderPath)
                    dragController.hoveredFolderPath = null
                    dragController.hoveredFolderPaneIndex = null
                }
            }
        }
    }

    // 拖曳懸停於分區上方返回鈕滿 2 秒（顯示放大鏡特效）-> 自動跳回上層資料夾
    LaunchedEffect(dragController.isDragging, dragController.hoveredBackButtonPane) {
        val backPane = dragController.hoveredBackButtonPane
        if (dragController.isDragging && (backPane == 2 || backPane == 3)) {
            delay(2000L)
            if (dragController.isDragging && dragController.hoveredBackButtonPane == backPane) {
                DxHaptics.lightVibrate(activityContext, hapticView)
                viewModel.navigateUp(backPane)
                dragController.hoveredBackButtonPane = null
            }
        }
    }

    BackHandler(enabled = true) {
        if (bottomActionBarState != null || pane2State.selectedPaths.isNotEmpty() || pane3State.selectedPaths.isNotEmpty()) {
            bottomActionBarState = null
            if (pane2State.selectedPaths.isNotEmpty()) viewModel.clearSelection(2)
            if (pane3State.selectedPaths.isNotEmpty()) viewModel.clearSelection(3)
            return@BackHandler
        }
        val targetPane = when {
            focusedPane == 3 && pane3Expanded -> 3
            pane2Expanded -> 2
            pane3Expanded -> 3
            else -> focusedPane
        }
        if (viewModel.canNavigateUp(targetPane)) {
            viewModel.navigateUp(targetPane)
        } else {
            showExitConfirmDialog = true
        }
    }

    fun activeFiles(paneIdx: Int): List<VfsFile> {
        val st = if (paneIdx == 2) pane2State else pane3State
        return st.files.filter { it.path in st.selectedPaths }
    }

    fun handleContextAction(paneIdx: Int, targetFile: VfsFile?, action: ContextMenuAction) {
        val selected = activeFiles(paneIdx).ifEmpty { listOfNotNull(targetFile) }
        val otherPane = if (paneIdx == 2) 3 else 2
        val otherState = if (otherPane == 2) pane2State else pane3State

        when (action) {
            ContextMenuAction.MULTI_SELECT -> targetFile?.let { viewModel.toggleSelection(paneIdx, it.path) }
            ContextMenuAction.RENAME -> if (selected.isNotEmpty()) renameTarget = paneIdx to selected
            ContextMenuAction.COPY -> if (selected.isNotEmpty()) viewModel.copyToClipboard(paneIdx, selected, isCut = false)
            ContextMenuAction.PASTE -> viewModel.pasteClipboard(paneIdx)
            ContextMenuAction.COMPRESS_OR_EXTRACT -> {
                val first = selected.firstOrNull() ?: return
                val isArchive = first.name.lowercase().let {
                    it.endsWith(".zip") || it.endsWith(".7z") || it.endsWith(".tar") || it.endsWith(".gz") || it.endsWith(".tgz")
                }
                if (isArchive) {
                    passwordPrompt = Triple("解壓縮 ${first.name}", "密碼（若無加密請留空）") { pwd ->
                        viewModel.extractArchive(paneIdx, first, pwd.ifEmpty { null })
                    }
                } else {
                    passwordPrompt = Triple("壓縮為 ZIP", "AES-256 加密密碼（可留空）") { pwd ->
                        viewModel.compressFiles(paneIdx, selected, pwd.ifEmpty { null })
                    }
                }
            }
            ContextMenuAction.ENCRYPT_OR_DECRYPT -> {
                val first = selected.firstOrNull() ?: return
                val isDxe = first.name.lowercase().endsWith(".dxe")
                passwordPrompt = Triple(
                    if (isDxe) "解密 ${first.name}" else "AES-256 加密 ${first.name}",
                    "請輸入密碼"
                ) { pwd -> viewModel.encryptOrDecryptDxe(paneIdx, first, pwd) }
            }
            ContextMenuAction.OPEN_WITH -> targetFile?.let { viewModel.openWithSystemChooser(File(it.path), it.mimeType, forceAsk = true) }
            ContextMenuAction.RESET_DEFAULT_APP -> {
                val f = targetFile ?: selected.firstOrNull { !it.isDirectory }
                if (f != null) {
                    viewModel.clearDefaultOpenApp(File(f.path), f.mimeType)
                } else {
                    viewModel.clearAllDefaultOpenApps()
                }
            }
            ContextMenuAction.OPEN_DIRECTORY -> targetFile?.let {
                val parent = File(it.path).parent ?: return@let
                viewModel.navigateToPath(paneIdx, parent)
            }
            ContextMenuAction.SEND_TO_OTHER_PANE -> if (selected.isNotEmpty()) {
                if (otherPane == 3) viewModel.setPaneExpanded(isExpandedScreen, 3, true)
                val curMount = if (paneIdx == 2) pane2State.mountId else pane3State.mountId
                viewModel.performTransfer(curMount, selected, otherState.mountId, otherState.currentPath, isMove = false)
            }
            ContextMenuAction.CREATE_SHORTCUT -> {
                val targets = if (selected.isNotEmpty()) selected else listOfNotNull(targetFile)
                if (targets.isNotEmpty()) {
                    ShortcutHelper.pinFilesToDesktop(activityContext, targets) {
                        android.widget.Toast.makeText(
                            activityContext,
                            "目前啟動器不支援或未授權建立桌面捷徑",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            ContextMenuAction.INSTALL_WITH_SHIZUKU -> {
                val apks = selected.filter { !it.isDirectory && it.name.lowercase().endsWith(".apk") }
                    .map { File(it.path) }
                if (apks.isNotEmpty()) {
                    viewModel.installApksWithShizuku(apks)
                }
            }
            ContextMenuAction.PROPERTIES -> targetFile?.let { propertiesTarget = it }
            ContextMenuAction.DELETE -> if (selected.isNotEmpty()) pendingDeleteRequest = Triple(paneIdx, selected, false)
        }
    }

    val isGlassGradient = settings.darkTheme && settings.darkBackgroundStyle == DarkBackgroundStyle.BLACK_TO_GRAY_GLASS
    val rootBgModifier = if (isGlassGradient) {
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF000000),
                    Color(0xFF14161A),
                    Color(0xFF282B32)
                )
            )
        )
    } else {
        Modifier.background(MaterialTheme.colorScheme.background)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(rootBgModifier)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp, bottom = 6.dp)
            .dxKeyboardShortcuts { shortcut ->
                val curPane = focusedPane
                val curState = if (curPane == 2) pane2State else pane3State
                val selected = activeFiles(curPane)
                when (shortcut) {
                    DxShortcutAction.SELECT_ALL -> viewModel.selectAll(curPane)
                    DxShortcutAction.CLEAR_SELECTION -> viewModel.clearSelection(curPane)
                    DxShortcutAction.COPY -> if (selected.isNotEmpty()) viewModel.copyToClipboard(curPane, selected, false)
                    DxShortcutAction.CUT -> if (selected.isNotEmpty()) viewModel.copyToClipboard(curPane, selected, true)
                    DxShortcutAction.PASTE -> viewModel.pasteClipboard(curPane)
                    DxShortcutAction.RENAME -> if (selected.isNotEmpty()) renameTarget = curPane to selected
                    DxShortcutAction.DELETE_TO_TRASH -> if (selected.isNotEmpty()) pendingDeleteRequest = Triple(curPane, selected, false)
                    DxShortcutAction.DELETE_PERMANENT -> if (selected.isNotEmpty()) pendingDeleteRequest = Triple(curPane, selected, true)
                    DxShortcutAction.SEND_TO_OTHER_PANE -> handleContextAction(curPane, selected.firstOrNull(), ContextMenuAction.SEND_TO_OTHER_PANE)
                    DxShortcutAction.CYCLE_FOCUS_PANE -> viewModel.focusedPaneIndex.value = if (curPane == 2) 3 else 2
                    DxShortcutAction.TOGGLE_SIDEBAR -> viewModel.setPaneExpanded(isExpandedScreen, 1, !pane1Expanded)
                    DxShortcutAction.TOGGLE_DUAL_PANE -> viewModel.setPaneExpanded(isExpandedScreen, 3, !pane3Expanded)
                    DxShortcutAction.COMPRESS -> handleContextAction(curPane, selected.firstOrNull(), ContextMenuAction.COMPRESS_OR_EXTRACT)
                    DxShortcutAction.EXTRACT -> handleContextAction(curPane, selected.firstOrNull(), ContextMenuAction.COMPRESS_OR_EXTRACT)
                    DxShortcutAction.ENCRYPT_DECRYPT -> handleContextAction(curPane, selected.firstOrNull(), ContextMenuAction.ENCRYPT_OR_DECRYPT)
                    DxShortcutAction.PROPERTIES -> propertiesTarget = selected.firstOrNull() ?: curState.files.firstOrNull()
                    DxShortcutAction.SHOW_SHORTCUT_CHEATSHEET -> showShortcutsDialog = true
                }
            }
    ) {
        ThreePaneScaffold(
            isExpandedScreen = isExpandedScreen,
            isLargeTablet = isLargeTablet,
            showPane3 = showPane3,
            isRatioEditMode = isRatioEditMode,
            onToggleRatioEditMode = { isRatioEditMode = !isRatioEditMode },
            onOpenAiAssistant = { showAiAssistantDialog = true },
            onOpenSettings = { showSettingsDialog = true },
            pane1Expanded = pane1Expanded,
            pane2Expanded = pane2Expanded,
            pane3Expanded = pane3Expanded,
            onPaneExpandedChange = { idx, exp ->
                if (isSmallWindowMode) {
                    if (idx == 1) smallWinPane1Expanded = exp
                } else {
                    viewModel.setPaneExpanded(isExpandedScreen, idx, exp)
                }
            },
            autoCollapseUnusedPaneOnDrag = dragController.activePaneIndexDuringDrag,
            keepAliveSourcePane = if (dragController.isHolding) dragController.sourcePaneIndex else null,
            hoveredExpandButtonPane = dragController.hoveredExpandButtonPane,
            onExpandButtonBoundsChanged = { idx, rect -> dragController.updateExpandButtonBounds(idx, rect) },
            onPaneBoundsChanged = { idx, rect -> dragController.updatePaneBounds(idx, rect) },
            pane1Content = {
                NavigationPane(
                    localStorageSpace = storageSpace,
                    externalStorages = externalStorages,
                    cloudMounts = settings.cloudMounts,
                    bookmarks = settings.bookmarks,
                    trashCount = trashItems.size,
                    showHiddenFiles = settings.showHiddenFiles,
                    dragController = dragController,
                    onGoHome = {
                        viewModel.navigateToPath(
                            focusedPane,
                            Environment.getExternalStorageDirectory().absolutePath,
                            "local",
                            "內部儲存空間"
                        )
                    },
                    onOpenSettings = { showSettingsDialog = true },
                    onToggleDualPane = { viewModel.setPaneExpanded(isExpandedScreen, 3, !pane3Expanded) },
                    showDualPaneToggle = !isSmallWindowMode,
                    isMultiWindowLocked = licenseStatus.isMultiWindowLocked,
                    onOpenNewWindow = {
                        ProLicenseManager.refreshStatus(activityContext)
                        if (ProLicenseManager.status.value.isMultiWindowLocked) {
                            proUpgradeReason = "7 天免費試用已結束：自由小視窗多開模式需升級 Pro 版才可使用。"
                            showProUpgradeDialog = true
                        } else if (SystemCrossAppDragHelper.isFreeformGuideSuppressed(activityContext)) {
                            SystemCrossAppDragHelper.openNewWindow(activityContext)
                        } else {
                            dontShowFreeformGuideAgain = false
                            showFreeformGuideDialog = true
                        }
                    },
                    onOpenLocalRoot = {
                        viewModel.navigateToPath(
                            focusedPane,
                            Environment.getExternalStorageDirectory().absolutePath,
                            "local",
                            "內部儲存空間"
                        )
                    },
                    onOpenExternalStorage = { ext ->
                        viewModel.navigateToPath(
                            focusedPane,
                            ext.path,
                            "local",
                            ext.label
                        )
                    },
                    onRefreshStorages = {
                        viewModel.refreshStorageSpace()
                    },
                    onOpenCloudMount = { mount -> viewModel.openCloudMount(focusedPane, mount) },
                    onRemoveCloudMount = { mountId -> viewModel.removeCloudMount(mountId) },
                    onOpenCloudWizard = {
                        ProLicenseManager.refreshStatus(activityContext)
                        if (!ProLicenseManager.status.value.hasProAccess && settings.cloudMounts.size >= 2) {
                            proUpgradeReason = "7 天免費試用已結束：免費版最多只能掛載 2 個雲端／網路硬碟（且單一類別僅限 1 個帳號）。"
                            showProUpgradeDialog = true
                        } else {
                            showCloudWizard = true
                        }
                    },
                    onOpenCategory = { cat -> viewModel.openCategory(focusedPane, cat) },
                    onOpenBookmark = { path -> viewModel.navigateToPath(focusedPane, path, "local", "內部儲存空間") },
                    onRemoveBookmark = { path -> viewModel.removeBookmark(path) }
                )
            },
            pane2Content = { onCollapse ->
                FileExplorerPane(
                    state = pane2State,
                    isFocused = focusedPane == 2,
                    dragController = dragController,
                    installedApps = installedApps,
                    includeSystemApps = includeSystemApps,
                    selectedAppPackages = selectedAppPackages,
                    trashItems = trashItems,
                    onFocusPane = { viewModel.focusedPaneIndex.value = 2 },
                    onCollapsePane = onCollapse,
                    onNavigateUp = { viewModel.navigateUp(2) },
                    onNavigateBack = { viewModel.navigateBack(2) },
                    onNavigateForward = { viewModel.navigateForward(2) },
                    onNavigateToPath = { path -> viewModel.navigateToPath(2, path) },
                    onOpenFile = { viewModel.openFile(it) },
                    onToggleSelect = { viewModel.toggleSelection(2, it) },
                    onSelectAll = { viewModel.selectAll(2) },
                    onClearSelection = { viewModel.clearSelection(2) },
                    onRubberBandSelect = { viewModel.setSelection(2, it) },
                    resolvePaneTarget = { idx ->
                        when (idx) {
                            1 -> "local" to Environment.getExternalStorageDirectory().absolutePath
                            3 -> pane3State.mountId to pane3State.currentPath
                            else -> pane2State.mountId to pane2State.currentPath
                        }
                    },
                    onShowContextMenu = { file -> contextMenuTarget = 2 to file },
                    onSearchChange = { viewModel.updateSearchQuery(2, it) },
                    onViewModeChange = { viewModel.setViewMode(2, it) },
                    onCtrlWheelZoom = { viewModel.adjustIconScale(2, it) },
                    onAddBookmark = { viewModel.addBookmark(it) },
                    onCreateNewFolderClick = { showNewFolderForPane = 2 },
                    hasClipboard = clipboard != null && clipboard!!.files.isNotEmpty(),
                    clipboardCount = clipboard?.files?.size ?: 0,
                    onPasteClick = { viewModel.pasteClipboard(2) },
                    onShowBottomActionBar = { prioritize -> bottomActionBarState = 2 to prioritize },
                    onHideBottomActionBar = { bottomActionBarState = null },
                    isBottomActionBarVisible = bottomActionBarState?.first == 2,
                    onSwitchCategory = { cat -> viewModel.openCategory(2, cat) },
                    onToggleAppSelection = { viewModel.toggleAppPackageSelection(it) },
                    onToggleSystemApps = { viewModel.toggleSystemApps() },
                    onExportSelectedApps = { viewModel.exportSelectedApps(selectedAppPackages.toList()) },
                    onRestoreTrashItem = { viewModel.restoreTrashItem(it) },
                    onEmptyTrash = { showEmptyTrashConfirmDialog = true },
                    windowPartitionLabel = if (isSmallWindowMode) s.partitionLabel(windowSlot) else null,
                    isSmallWindowMode = isSmallWindowMode
                )
            },
            pane3Content = { onCollapse ->
                FileExplorerPane(
                    state = pane3State,
                    isFocused = focusedPane == 3,
                    dragController = dragController,
                    installedApps = installedApps,
                    includeSystemApps = includeSystemApps,
                    selectedAppPackages = selectedAppPackages,
                    trashItems = trashItems,
                    onFocusPane = { viewModel.focusedPaneIndex.value = 3 },
                    onCollapsePane = onCollapse,
                    onNavigateUp = { viewModel.navigateUp(3) },
                    onNavigateBack = { viewModel.navigateBack(3) },
                    onNavigateForward = { viewModel.navigateForward(3) },
                    onNavigateToPath = { path -> viewModel.navigateToPath(3, path) },
                    onOpenFile = { viewModel.openFile(it) },
                    onToggleSelect = { viewModel.toggleSelection(3, it) },
                    onSelectAll = { viewModel.selectAll(3) },
                    onClearSelection = { viewModel.clearSelection(3) },
                    onRubberBandSelect = { viewModel.setSelection(3, it) },
                    resolvePaneTarget = { idx ->
                        when (idx) {
                            1 -> "local" to Environment.getExternalStorageDirectory().absolutePath
                            2 -> pane2State.mountId to pane2State.currentPath
                            else -> pane3State.mountId to pane3State.currentPath
                        }
                    },
                    onShowContextMenu = { file -> contextMenuTarget = 3 to file },
                    onSearchChange = { viewModel.updateSearchQuery(3, it) },
                    onViewModeChange = { viewModel.setViewMode(3, it) },
                    onCtrlWheelZoom = { viewModel.adjustIconScale(3, it) },
                    onAddBookmark = { viewModel.addBookmark(it) },
                    onCreateNewFolderClick = { showNewFolderForPane = 3 },
                    hasClipboard = clipboard != null && clipboard!!.files.isNotEmpty(),
                    clipboardCount = clipboard?.files?.size ?: 0,
                    onPasteClick = { viewModel.pasteClipboard(3) },
                    onShowBottomActionBar = { prioritize -> bottomActionBarState = 3 to prioritize },
                    onHideBottomActionBar = { bottomActionBarState = null },
                    isBottomActionBarVisible = bottomActionBarState?.first == 3,
                    onSwitchCategory = { cat -> viewModel.openCategory(3, cat) },
                    onToggleAppSelection = { viewModel.toggleAppPackageSelection(it) },
                    onToggleSystemApps = { viewModel.toggleSystemApps() },
                    onExportSelectedApps = { viewModel.exportSelectedApps(selectedAppPackages.toList()) },
                    onRestoreTrashItem = { viewModel.restoreTrashItem(it) },
                    onEmptyTrash = { showEmptyTrashConfirmDialog = true },
                    windowPartitionLabel = if (isSmallWindowMode) s.partitionLabel(windowSlot) else null,
                    isSmallWindowMode = isSmallWindowMode
                )
            }
        )

        // 全域雙指堆疊拖曳預覽層
        DragAndDropOverlay(dragController)

        // 跨 App 多選拖曳批次傳遞佇列（如 LINE 一次僅接收單檔時，由此接手逐一或整批傳送剩餘檔案）
        val batchQueue = SystemCrossAppDragHelper.batchQueueFiles
        if (batchQueue.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "批次傳送剩餘 ${batchQueue.size} 檔",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = {
                            val next = batchQueue.removeAt(0)
                            SystemCrossAppDragHelper.shareFilesViaSystem(activityContext, listOf(next))
                        }
                    ) {
                        Text("逐一傳下個")
                    }
                    TextButton(
                        onClick = {
                            val rest = batchQueue.toList()
                            SystemCrossAppDragHelper.clearBatchQueue()
                            SystemCrossAppDragHelper.shareFilesViaSystem(activityContext, rest)
                        }
                    ) {
                        Text("全部一次傳")
                    }
                    IconButton(onClick = { SystemCrossAppDragHelper.clearBatchQueue() }) {
                        Icon(Icons.Default.Close, contentDescription = "關閉批次傳送")
                    }
                }
            }
        }

        // 長壓 1.5 秒多選底部操作選單（第二指多選或開始拖曳時自動隱藏；原地放開優先顯示新增資料夾/壓縮）
        val activeBarPane = bottomActionBarState?.first
        val activeBarSelected = activeBarPane?.let { activeFiles(it) }.orEmpty()
        val isBarVisible = bottomActionBarState != null &&
            activeBarSelected.isNotEmpty() &&
            !dragController.isDragging
        AnimatedVisibility(
            visible = isBarVisible,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                initialOffsetY = { it }
            ) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            val paneIdx = activeBarPane ?: 2
            val prioritizeCreateOrCompress = bottomActionBarState?.second == true
            val selectedList = activeFiles(paneIdx)
            MultiSelectBottomActionBar(
                selectedCount = selectedList.size,
                prioritizeCreateOrCompress = prioritizeCreateOrCompress,
                onNewFolder = {
                    bottomActionBarState = null
                    showNewFolderForPane = paneIdx
                },
                onCompress = {
                    bottomActionBarState = null
                    handleContextAction(paneIdx, selectedList.firstOrNull(), ContextMenuAction.COMPRESS_OR_EXTRACT)
                },
                onCopy = {
                    bottomActionBarState = null
                    if (selectedList.isNotEmpty()) viewModel.copyToClipboard(paneIdx, selectedList, isCut = false)
                },
                onMove = {
                    bottomActionBarState = null
                    if (selectedList.isNotEmpty()) viewModel.copyToClipboard(paneIdx, selectedList, isCut = true)
                },
                onOpen = {
                    bottomActionBarState = null
                    selectedList.firstOrNull()?.let {
                        if (it.isDirectory) viewModel.navigateToPath(paneIdx, it.path)
                        else viewModel.openWithSystemChooser(File(it.path), it.mimeType)
                    }
                },
                onDelete = {
                    bottomActionBarState = null
                    if (selectedList.isNotEmpty()) pendingDeleteRequest = Triple(paneIdx, selectedList, false)
                },
                onCreateShortcut = {
                    bottomActionBarState = null
                    if (selectedList.isNotEmpty()) {
                        ShortcutHelper.pinFilesToDesktop(activityContext, selectedList) {
                            android.widget.Toast.makeText(
                                activityContext,
                                "目前啟動器不支援或未授權建立桌面捷徑",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onMore = {
                    bottomActionBarState = null
                    contextMenuTarget = paneIdx to selectedList.firstOrNull()
                },
                onDismiss = {
                    bottomActionBarState = null
                    viewModel.clearSelection(paneIdx)
                }
            )
        }

        // 拖曳放開「複製或移動」詢問視窗
        dragController.pendingDropTarget?.let { req ->
            val srcMount = if (req.sourcePaneIndex == 2) pane2State.mountId else pane3State.mountId
            CopyOrMoveDialog(
                request = req,
                onCopy = {
                    dragController.playDropIntoFolderAnimation(req)
                    viewModel.performTransfer(srcMount, req.files, req.targetMountId, req.targetDirPath, isMove = false)
                    dragController.pendingDropTarget = null
                },
                onMove = {
                    dragController.playDropIntoFolderAnimation(req)
                    viewModel.performTransfer(srcMount, req.files, req.targetMountId, req.targetDirPath, isMove = true)
                    dragController.pendingDropTarget = null
                },
                onDismiss = { dragController.pendingDropTarget = null }
            )
        }

        contextMenuTarget?.let { (paneIdx, targetFile) ->
            val selectedList = activeFiles(paneIdx)
            val selCount = selectedList.size
            val hasApk = selectedList.any { !it.isDirectory && it.name.lowercase().endsWith(".apk") }
            FileContextMenuDialog(
                targetFile = targetFile,
                selectedCount = selCount,
                hasClipboard = clipboard != null,
                hasApkSelected = hasApk,
                onAction = { action -> handleContextAction(paneIdx, targetFile, action) },
                onDismiss = { contextMenuTarget = null }
            )
        }

        renameTarget?.let { (paneIdx, files) ->
            RenameDialog(
                initialName = if (files.size == 1) files.first().name else "file",
                isBatch = files.size > 1,
                onConfirm = { newName -> viewModel.renameFiles(paneIdx, files, newName) },
                onDismiss = { renameTarget = null }
            )
        }

        passwordPrompt?.let { (title, label, onConfirm) ->
            PasswordInputDialog(
                title = title,
                label = label,
                allowEmpty = title.contains("壓縮"),
                onConfirm = onConfirm,
                onDismiss = { passwordPrompt = null }
            )
        }

        propertiesTarget?.let { file ->
            FilePropertiesDialog(file = file, onDismiss = { propertiesTarget = null })
        }

        showNewFolderForPane?.let { paneIdx ->
            NewFolderDialog(
                onConfirm = { name -> viewModel.createFolder(paneIdx, name) },
                onDismiss = { showNewFolderForPane = null }
            )
        }

        if (showEmptyTrashConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showEmptyTrashConfirmDialog = false },
                title = { Text("清空垃圾桶") },
                text = { Text("確定要永久刪除垃圾桶中的 ${trashItems.size} 個項目嗎？此動作無法復原。") },
                confirmButton = {
                    Button(
                        onClick = {
                            showEmptyTrashConfirmDialog = false
                            DxHaptics.trashEmptyVibrate(activityContext, hapticView)
                            DxSoundEffects.playTrashEmptySound()
                            viewModel.emptyTrash()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("確定清空")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyTrashConfirmDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        pendingDeleteRequest?.let { (paneIdx, files, forcePermanent) ->
            val willUseTrash = !forcePermanent && settings.recycleBinEnabled
            AlertDialog(
                onDismissRequest = { pendingDeleteRequest = null },
                title = { Text(if (willUseTrash) "移至垃圾桶" else "永久刪除檔案") },
                text = {
                    Text(
                        if (willUseTrash) {
                            "確定要將選取的 ${files.size} 個項目丟入垃圾桶嗎？（垃圾桶項目會保留 30 天）"
                        } else {
                            "確定要永久刪除選取的 ${files.size} 個項目嗎？此動作無法復原。"
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingDeleteRequest = null
                            viewModel.deleteFiles(paneIdx, files, forcePermanent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(if (willUseTrash) "丟入垃圾桶" else "永久刪除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteRequest = null }) {
                        Text("取消")
                    }
                }
            )
        }

        if (showShortcutsDialog) {
            ShortcutCheatSheetDialog(onDismiss = { showShortcutsDialog = false })
        }

        if (showCloudWizard) {
            CloudMountWizardDialog(
                onMountCreated = { newMount ->
                    val check = ProLicenseManager.canAddCloudMount(
                        context = activityContext,
                        existingMounts = settings.cloudMounts,
                        targetProtocol = newMount.protocol
                    )
                    if (check.isSuccess) {
                        viewModel.saveCloudMount(newMount)
                    } else {
                        proUpgradeReason = check.exceptionOrNull()?.message
                        showProUpgradeDialog = true
                    }
                },
                onDismiss = { showCloudWizard = false }
            )
        }

        if (showSettingsDialog) {
            SettingsAndAboutDialog(
                settings = settings,
                onToggleRecycleBin = { viewModel.setRecycleBinEnabled(it) },
                onToggleShizukuInstall = { viewModel.setUseShizukuInstall(it) },
                onToggleShowHidden = { viewModel.setShowHiddenFiles(it) },
                onToggleDarkTheme = { viewModel.setDarkTheme(it) },
                onChangeDarkBackgroundStyle = { viewModel.setDarkBackgroundStyle(it) },
                onChangeLanguage = { viewModel.setLanguage(it) },
                onChangeAiProvider = { viewModel.setAiProvider(it) },
                onSaveAiApiKey = { provider, key -> viewModel.setAiApiKey(provider, key) },
                onChangeAiModel = { provider, model -> viewModel.setAiModel(provider, model) },
                onClearDefaultOpenApps = { viewModel.clearAllDefaultOpenApps() },
                onToggleAiThinking = { viewModel.setAiThinkingEnabled(it) },
                onOpenStorageAnalyzer = { showStorageAnalyzer = true },
                onOpenWebShare = { showWebShare = true },
                onOpenShortcuts = { showShortcutsDialog = true },
                onInstallUpdateApk = { apk -> viewModel.installApkWithShizukuOrFallback(apk) },
                onDismiss = { showSettingsDialog = false }
            )
        }

        if (showAiAssistantDialog) {
            val targetPaneIdx = if (focusedPane == 3 && pane3Expanded) 3 else 2
            val targetState = if (targetPaneIdx == 3) pane3State else pane2State
            AiFileAssistantDialog(
                settings = settings,
                paneState = targetState,
                onSelectProvider = { viewModel.setAiProvider(it) },
                onSaveApiKey = { provider, key -> viewModel.setAiApiKey(provider, key) },
                onSelectModel = { provider, model -> viewModel.setAiModel(provider, model) },
                onToggleThinking = { viewModel.setAiThinkingEnabled(it) },
                onApplyOrganizeMoves = { moves -> viewModel.organizeFilesWithPlan(targetPaneIdx, moves) },
                onDismiss = { showAiAssistantDialog = false }
            )
        }

        if (showStorageAnalyzer) {
            val activeState = if (focusedPane == 2) pane2State else pane3State
            val dir = File(activeState.currentPath).takeIf { it.exists() && it.isDirectory }
                ?: Environment.getExternalStorageDirectory()
            DxStorageAnalyzerDialog(
                targetDir = dir,
                onNavigateToFolder = { path -> viewModel.navigateToPath(focusedPane, path) },
                onDismiss = { showStorageAnalyzer = false }
            )
        }

        if (showWebShare) {
            val activeState = if (focusedPane == 2) pane2State else pane3State
            val dir = File(activeState.currentPath).takeIf { it.exists() && it.isDirectory }
                ?: Environment.getExternalStorageDirectory()
            DxWebShareDialog(
                shareRoot = dir,
                onDismiss = { showWebShare = false }
            )
        }

        if (showExitConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showExitConfirmDialog = false },
                title = { Text(s.exitAppTitle) },
                text = { Text(s.exitAppMessage) },
                confirmButton = {
                    Button(onClick = {
                        showExitConfirmDialog = false
                        (activityContext as? Activity)?.finish()
                    }) {
                        Text(s.exitAction)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirmDialog = false }) {
                        Text(s.cancel)
                    }
                }
            )
        }

        if (showFreeformGuideDialog) {
            AlertDialog(
                onDismissRequest = { showFreeformGuideDialog = false },
                title = { Text(s.freeformGuideTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = s.freeformGuideBody,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = s.freeformGuideItems,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = s.freeformGuideNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .clickable { dontShowFreeformGuideAgain = !dontShowFreeformGuideAgain }
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = dontShowFreeformGuideAgain,
                                onCheckedChange = { dontShowFreeformGuideAgain = it }
                            )
                            Text(s.freeformDontRemind, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (dontShowFreeformGuideAgain) {
                            SystemCrossAppDragHelper.setFreeformGuideSuppressed(activityContext, true)
                        }
                        showFreeformGuideDialog = false
                        SystemCrossAppDragHelper.openNewWindow(activityContext)
                    }) {
                        Text(s.freeformOpenNewWindow)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (dontShowFreeformGuideAgain) {
                            SystemCrossAppDragHelper.setFreeformGuideSuppressed(activityContext, true)
                        }
                        showFreeformGuideDialog = false
                        com.dx9901mk5.filemanager.ui.dialogs.openSettingsWithScrollHighlight(
                            context = activityContext,
                            action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
                            packageName = null,
                            highlightKey = "enable_freeform_support"
                        )
                    }) {
                        Text(s.freeformGoDevSettings)
                    }
                }
            )
        }

        openWithPrompt?.let { prompt ->
            var setAsDefault by remember(prompt) { mutableStateOf(true) }
            val pm = activityContext.packageManager
            AlertDialog(
                onDismissRequest = { viewModel.dismissOpenWithPrompt() },
                title = {
                    Text(
                        text = "選擇開啟應用程式",
                        maxLines = 1
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${prompt.file.name} (${prompt.mimeType})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { setAsDefault = !setAsDefault },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = setAsDefault,
                                onCheckedChange = { setAsDefault = it }
                            )
                            Text(
                                text = "設為此類檔案的預設開啟 App（下次直接開啟）",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            items(prompt.candidates.size) { idx ->
                                val cand = prompt.candidates[idx]
                                val appIconBitmap = remember(cand.packageName) {
                                    runCatching {
                                        pm.getApplicationIcon(cand.packageName)
                                            .toBitmap(width = 96, height = 96)
                                            .asImageBitmap()
                                    }.getOrNull()
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.launchOpenWithCandidate(prompt, cand, setAsDefault)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (appIconBitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = appIconBitmap,
                                            contentDescription = cand.appLabel,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cand.appLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        if (cand.packageName == prompt.currentDefaultPkg) {
                                            Text(
                                                text = "目前預設",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.launchSystemOpenChooser(prompt) }) {
                        Text("系統選單")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissOpenWithPrompt() }) {
                        Text("取消")
                    }
                }
            )
        }
        if (showProUpgradeDialog) {
            ProUpgradeDialog(
                reasonMessage = proUpgradeReason,
                onDismiss = {
                    showProUpgradeDialog = false
                    proUpgradeReason = null
                }
            )
        }
    }
}

@Composable
private fun MultiSelectBottomActionBar(
    selectedCount: Int,
    prioritizeCreateOrCompress: Boolean,
    onNewFolder: () -> Unit,
    onCompress: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onCreateShortcut: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalDxStrings.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
        shadowElevation = 12.dp,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.selectedCountLabel(selectedCount),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            if (prioritizeCreateOrCompress) {
                BottomBarActionButton(Icons.Default.CreateNewFolder, s.newFolder, highlight = true, onClick = onNewFolder)
                BottomBarActionButton(Icons.Default.Archive, s.compress, highlight = true, onClick = onCompress)
                BottomBarActionButton(Icons.Default.ContentCopy, s.copy, onClick = onCopy)
                BottomBarActionButton(Icons.Default.DriveFileMove, s.move, onClick = onMove)
                BottomBarActionButton(Icons.Default.OpenInNew, s.open, onClick = onOpen)
            } else {
                BottomBarActionButton(Icons.Default.ContentCopy, s.copy, onClick = onCopy)
                BottomBarActionButton(Icons.Default.DriveFileMove, s.move, onClick = onMove)
                BottomBarActionButton(Icons.Default.OpenInNew, s.open, onClick = onOpen)
                BottomBarActionButton(Icons.Default.Archive, s.compress, onClick = onCompress)
                BottomBarActionButton(Icons.Default.CreateNewFolder, s.newFolder, onClick = onNewFolder)
            }
            BottomBarActionButton(Icons.Default.AddToHomeScreen, s.pinDesktopShortcut, onClick = onCreateShortcut)
            BottomBarActionButton(Icons.Default.Delete, s.delete, isDestructive = true, onClick = onDelete)
            BottomBarActionButton(Icons.Default.MoreHoriz, s.more, onClick = onMore)
            BottomBarActionButton(Icons.Default.Close, s.cancel, onClick = onDismiss)
        }
    }
}

@Composable
private fun BottomBarActionButton(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = when {
        isDestructive -> MaterialTheme.colorScheme.error
        highlight -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1
        )
    }
}

private fun isGithubEditionExpired(): Boolean {
    val cutoff = Calendar.getInstance().apply {
        set(2026, Calendar.DECEMBER, 15, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return System.currentTimeMillis() >= cutoff
}

@Composable
private fun GithubExpiredBlockingScreen(
    onOpenPlayStore: () -> Unit,
    onExit: () -> Unit
) {
    val s = LocalDxStrings.current
    BackHandler { onExit() }
    val (title, message, playBtnText) = when (s.done) {
        "Done" -> Triple(
            "GitHub Version Expired",
            "This GitHub version expired on Dec 15, 2026 and can no longer be launched.\n\nPlease search for \"DX File Manager\" on Google Play Store to install the official release.",
            "Go to Google Play Store"
        )
        "完了" -> Triple(
            "GitHub 版の有効期限が切れました",
            "この GitHub 版は 2026 年 12 月 15 日をもって利用期限が終了し、起動できなくなりました。\n\nGoogle Play ストアで「DX ファイルマネージャー」を検索して正式版をインストールしてください。",
            "Google Play ストアへ"
        )
        "完成" -> if (s.selectAll == "全选") Triple(
            "GitHub 版已到期停用",
            "此 GitHub 版本已于 2026 年 12 月 15 日到期，无法再启动使用。\n\n请前往 Google Play 商店搜索「DX文件管理器」下载正式版。",
            "前往 Google Play 商店"
        ) else Triple(
            "GitHub 版已到期停用",
            "此 GitHub 版本已於 2026 年 12 月 15 日到期，無法再啟動使用。\n\n請前往 Google Play 商店搜尋「DX檔案管理器」下載正式版。",
            "前往 Google Play 商店"
        )
        else -> Triple(
            "GitHub 版已到期停用",
            "此 GitHub 版本已於 2026 年 12 月 15 日到期，無法再啟動使用。\n\n請前往 Google Play 商店搜尋「DX檔案管理器」下載正式版。",
            "前往 Google Play 商店"
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onExit) {
                        Text(s.exitAction)
                    }
                    Button(onClick = onOpenPlayStore) {
                        Text(playBtnText)
                    }
                }
            }
        }
    }
}
