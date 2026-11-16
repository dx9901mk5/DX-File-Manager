package com.dx9901mk5.filemanager

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.dx9901mk5.filemanager.ui.dialogs.CloudMountWizardDialog
import com.dx9901mk5.filemanager.ui.dialogs.ContextMenuAction
import com.dx9901mk5.filemanager.ui.dialogs.DxStorageAnalyzerDialog
import com.dx9901mk5.filemanager.ui.dialogs.DxWebShareDialog
import com.dx9901mk5.filemanager.ui.dialogs.FileContextMenuDialog
import com.dx9901mk5.filemanager.ui.dialogs.FilePropertiesDialog
import com.dx9901mk5.filemanager.ui.dialogs.NewFolderDialog
import com.dx9901mk5.filemanager.ui.dialogs.PasswordInputDialog
import com.dx9901mk5.filemanager.ui.dialogs.RenameDialog
import com.dx9901mk5.filemanager.ui.dialogs.SettingsAndAboutDialog
import com.dx9901mk5.filemanager.ui.dialogs.ShortcutCheatSheetDialog
import com.dx9901mk5.filemanager.ui.gesture.CopyOrMoveDialog
import com.dx9901mk5.filemanager.ui.gesture.CrossPaneDragController
import com.dx9901mk5.filemanager.ui.gesture.DragAndDropOverlay
import com.dx9901mk5.filemanager.ui.gesture.DxShortcutAction
import com.dx9901mk5.filemanager.ui.gesture.dxKeyboardShortcuts
import com.dx9901mk5.filemanager.ui.layout.FileExplorerPane
import com.dx9901mk5.filemanager.ui.layout.NavigationPane
import com.dx9901mk5.filemanager.ui.layout.ThreePaneScaffold
import com.dx9901mk5.filemanager.ui.theme.DxFileManagerTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: FileManagerViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureStoragePermission()
        ensureNotificationPermission()
        OAuth2AuthHelper.handleRedirectIntent(intent)

        intent.getStringExtra("shortcut_path")?.let { path ->
            val target = File(path)
            if (target.exists() && target.isDirectory) {
                viewModel.navigateToPath(2, target.absolutePath)
            }
        }

        setContent {
            val settings by viewModel.settings.collectAsState()
            val windowSizeClass = calculateWindowSizeClass(this)
            val isExpandedScreen = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
            val isLargeTablet = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

            DxFileManagerTheme(
                darkTheme = settings.darkTheme,
                darkBackgroundStyle = settings.darkBackgroundStyle
            ) {
                DxFileManagerScreen(
                    viewModel = viewModel,
                    isExpandedScreen = isExpandedScreen,
                    isLargeTablet = isLargeTablet
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        OAuth2AuthHelper.handleRedirectIntent(intent)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            runCatching {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}

@Composable
private fun DxFileManagerScreen(
    viewModel: FileManagerViewModel,
    isExpandedScreen: Boolean,
    isLargeTablet: Boolean
) {
    val settings by viewModel.settings.collectAsState()
    val pane2State by viewModel.pane2State.collectAsState()
    val pane3State by viewModel.pane3State.collectAsState()
    val focusedPane by viewModel.focusedPaneIndex.collectAsState()
    val storageSpace by viewModel.localStorageSpace.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val trashItems by viewModel.trashItems.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val includeSystemApps by viewModel.includeSystemApps.collectAsState()
    val selectedAppPackages by viewModel.selectedAppPackages.collectAsState()

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
    var showStorageAnalyzer by remember { mutableStateOf(false) }
    var showWebShare by remember { mutableStateOf(false) }
    var showNewFolderForPane by remember { mutableStateOf<Int?>(null) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    // Pair(paneIndex, prioritizeCreateOrCompress)
    var bottomActionBarState by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    var contextMenuTarget by remember { mutableStateOf<Pair<Int, VfsFile?>?>(null) }
    var renameTarget by remember { mutableStateOf<Pair<Int, List<VfsFile>>?>(null) }
    var passwordPrompt by remember { mutableStateOf<Triple<String, String, (String) -> Unit>?>(null) }
    var propertiesTarget by remember { mutableStateOf<VfsFile?>(null) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showPane3 = true
    var isRatioEditMode by remember { mutableStateOf(false) }

    val pane1Expanded = if (isExpandedScreen || isLandscape) settings.pane1ExpandedTablet else settings.pane1ExpandedCompact
    val pane2Expanded = if (isExpandedScreen || isLandscape) settings.pane2ExpandedTablet else settings.pane2ExpandedCompact
    val pane3Expanded = if (isExpandedScreen || isLandscape) settings.pane3ExpandedTablet else settings.pane3ExpandedCompact

    val activityContext = LocalContext.current
    BackHandler(enabled = true) {
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
            ContextMenuAction.OPEN_WITH -> targetFile?.let { viewModel.openWithSystemChooser(File(it.path), it.mimeType) }
            ContextMenuAction.OPEN_DIRECTORY -> targetFile?.let {
                val parent = File(it.path).parent ?: return@let
                viewModel.navigateToPath(paneIdx, parent)
            }
            ContextMenuAction.SEND_TO_OTHER_PANE -> if (selected.isNotEmpty()) {
                if (otherPane == 3) viewModel.setPaneExpanded(isExpandedScreen, 3, true)
                val curMount = if (paneIdx == 2) pane2State.mountId else pane3State.mountId
                viewModel.performTransfer(curMount, selected, otherState.mountId, otherState.currentPath, isMove = false)
            }
            ContextMenuAction.CREATE_SHORTCUT -> targetFile?.let { viewModel.createHomeScreenShortcut(it) }
            ContextMenuAction.PROPERTIES -> targetFile?.let { propertiesTarget = it }
            ContextMenuAction.DELETE -> if (selected.isNotEmpty()) viewModel.deleteFiles(paneIdx, selected)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    DxShortcutAction.DELETE_TO_TRASH -> if (selected.isNotEmpty()) viewModel.deleteFiles(curPane, selected, false)
                    DxShortcutAction.DELETE_PERMANENT -> if (selected.isNotEmpty()) viewModel.deleteFiles(curPane, selected, true)
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
            onOpenSettings = { showSettingsDialog = true },
            pane1Expanded = pane1Expanded,
            pane2Expanded = pane2Expanded,
            pane3Expanded = pane3Expanded,
            onPaneExpandedChange = { idx, exp -> viewModel.setPaneExpanded(isExpandedScreen, idx, exp) },
            autoCollapseUnusedPaneOnDrag = dragController.activePaneIndexDuringDrag,
            keepAliveSourcePane = if (dragController.isHolding) dragController.sourcePaneIndex else null,
            hoveredExpandButtonPane = dragController.hoveredExpandButtonPane,
            onExpandButtonBoundsChanged = { idx, rect -> dragController.updateExpandButtonBounds(idx, rect) },
            onPaneBoundsChanged = { idx, left, right -> dragController.updatePaneBounds(idx, left, right) },
            pane1Content = {
                NavigationPane(
                    localStorageSpace = storageSpace,
                    cloudMounts = settings.cloudMounts,
                    bookmarks = settings.bookmarks,
                    trashCount = trashItems.size,
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
                    onOpenLocalRoot = {
                        viewModel.navigateToPath(
                            focusedPane,
                            Environment.getExternalStorageDirectory().absolutePath,
                            "local",
                            "內部儲存空間"
                        )
                    },
                    onOpenCloudMount = { mount -> viewModel.openCloudMount(focusedPane, mount) },
                    onRemoveCloudMount = { mountId -> viewModel.removeCloudMount(mountId) },
                    onOpenCloudWizard = { showCloudWizard = true },
                    onOpenCategory = { cat -> viewModel.openCategory(focusedPane, cat) },
                    onOpenBookmark = { path -> viewModel.navigateToPath(focusedPane, path, "local", "內部儲存空間") }
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
                        val st = if (idx == 3) pane3State else pane2State
                        st.mountId to st.currentPath
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
                    onSwitchCategory = { cat -> viewModel.openCategory(2, cat) },
                    onToggleAppSelection = { viewModel.toggleAppPackageSelection(it) },
                    onToggleSystemApps = { viewModel.toggleSystemApps() },
                    onExportSelectedApps = { viewModel.exportSelectedApps(selectedAppPackages.toList()) },
                    onRestoreTrashItem = { viewModel.restoreTrashItem(it) },
                    onEmptyTrash = { viewModel.emptyTrash() }
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
                        val st = if (idx == 2) pane2State else pane3State
                        st.mountId to st.currentPath
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
                    onSwitchCategory = { cat -> viewModel.openCategory(3, cat) },
                    onToggleAppSelection = { viewModel.toggleAppPackageSelection(it) },
                    onToggleSystemApps = { viewModel.toggleSystemApps() },
                    onExportSelectedApps = { viewModel.exportSelectedApps(selectedAppPackages.toList()) },
                    onRestoreTrashItem = { viewModel.restoreTrashItem(it) },
                    onEmptyTrash = { viewModel.emptyTrash() }
                )
            }
        )

        // 全域雙指堆疊拖曳預覽層
        DragAndDropOverlay(dragController)

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
                    if (selectedList.isNotEmpty()) viewModel.deleteFiles(paneIdx, selectedList)
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
                    viewModel.performTransfer(srcMount, req.files, req.targetMountId, req.targetDirPath, isMove = false)
                    dragController.pendingDropTarget = null
                },
                onMove = {
                    viewModel.performTransfer(srcMount, req.files, req.targetMountId, req.targetDirPath, isMove = true)
                    dragController.pendingDropTarget = null
                },
                onDismiss = { dragController.pendingDropTarget = null }
            )
        }

        contextMenuTarget?.let { (paneIdx, targetFile) ->
            val selCount = if (paneIdx == 2) pane2State.selectedPaths.size else pane3State.selectedPaths.size
            FileContextMenuDialog(
                targetFile = targetFile,
                selectedCount = selCount,
                hasClipboard = clipboard != null,
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

        if (showShortcutsDialog) {
            ShortcutCheatSheetDialog(onDismiss = { showShortcutsDialog = false })
        }

        if (showCloudWizard) {
            CloudMountWizardDialog(
                onMountCreated = { viewModel.saveCloudMount(it) },
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
                onOpenStorageAnalyzer = { showStorageAnalyzer = true },
                onOpenWebShare = { showWebShare = true },
                onOpenShortcuts = { showShortcutsDialog = true },
                onInstallUpdateApk = { apk -> viewModel.installApkWithShizukuOrFallback(apk) },
                onDismiss = { showSettingsDialog = false }
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
                title = { Text("退出應用程式") },
                text = { Text("目前已在根目錄，確定要退出 DX檔案管理器 嗎？") },
                confirmButton = {
                    Button(onClick = {
                        showExitConfirmDialog = false
                        (activityContext as? Activity)?.finish()
                    }) {
                        Text("退出")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirmDialog = false }) {
                        Text("取消")
                    }
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
    onMore: () -> Unit,
    onDismiss: () -> Unit
) {
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
                text = "已選 $selectedCount",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            if (prioritizeCreateOrCompress) {
                BottomBarActionButton(Icons.Default.CreateNewFolder, "新增資料夾", highlight = true, onClick = onNewFolder)
                BottomBarActionButton(Icons.Default.Archive, "壓縮", highlight = true, onClick = onCompress)
                BottomBarActionButton(Icons.Default.ContentCopy, "複製", onClick = onCopy)
                BottomBarActionButton(Icons.Default.DriveFileMove, "移動", onClick = onMove)
                BottomBarActionButton(Icons.Default.OpenInNew, "開啟", onClick = onOpen)
            } else {
                BottomBarActionButton(Icons.Default.ContentCopy, "複製", onClick = onCopy)
                BottomBarActionButton(Icons.Default.DriveFileMove, "移動", onClick = onMove)
                BottomBarActionButton(Icons.Default.OpenInNew, "開啟", onClick = onOpen)
                BottomBarActionButton(Icons.Default.Archive, "壓縮", onClick = onCompress)
                BottomBarActionButton(Icons.Default.CreateNewFolder, "新增資料夾", onClick = onNewFolder)
            }
            BottomBarActionButton(Icons.Default.Delete, "刪除", isDestructive = true, onClick = onDelete)
            BottomBarActionButton(Icons.Default.MoreHoriz, "更多", onClick = onMore)
            BottomBarActionButton(Icons.Default.Close, "取消", onClick = onDismiss)
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
