package com.dx9901mk5.filemanager.ui.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.core.shizuku.InstalledAppInfo
import com.dx9901mk5.filemanager.core.trash.TrashItemEntity
import com.dx9901mk5.filemanager.core.vfs.FileCategory
import com.dx9901mk5.filemanager.core.vfs.VfsFile
import com.dx9901mk5.filemanager.ui.ExplorerPaneState
import com.dx9901mk5.filemanager.ui.FileViewMode
import com.dx9901mk5.filemanager.ui.PaneMode
import com.dx9901mk5.filemanager.ui.components.FileThumbnailIcon
import com.dx9901mk5.filemanager.ui.gesture.SystemCrossAppDragHelper
import com.dx9901mk5.filemanager.ui.components.IosGlassFolderIcon
import com.dx9901mk5.filemanager.ui.dialogs.formatFileSize
import com.dx9901mk5.filemanager.ui.gesture.CrossPaneDragController
import com.dx9901mk5.filemanager.ui.gesture.RubberBandOverlay
import com.dx9901mk5.filemanager.ui.gesture.dxFileTouchGesture
import com.dx9901mk5.filemanager.ui.gesture.dxMouseHandler
import com.dx9901mk5.filemanager.ui.gesture.dxVerticalOverscrollBounce
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileExplorerPane(
    state: ExplorerPaneState,
    isFocused: Boolean,
    dragController: CrossPaneDragController,
    installedApps: List<InstalledAppInfo>,
    includeSystemApps: Boolean,
    selectedAppPackages: Set<String>,
    trashItems: List<TrashItemEntity>,
    onFocusPane: () -> Unit,
    onCollapsePane: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    onOpenFile: (VfsFile) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRubberBandSelect: (Set<String>) -> Unit,
    resolvePaneTarget: (paneIndex: Int) -> Pair<String, String> = { state.mountId to state.currentPath },
    onShowContextMenu: (VfsFile?) -> Unit,
    onSearchChange: (String) -> Unit,
    onViewModeChange: (FileViewMode) -> Unit,
    onCtrlWheelZoom: (Float) -> Unit,
    onAddBookmark: (String) -> Unit,
    onCreateNewFolderClick: () -> Unit,
    hasClipboard: Boolean = false,
    clipboardCount: Int = 0,
    onPasteClick: () -> Unit = {},
    onShowBottomActionBar: (prioritizeCreateOrCompress: Boolean) -> Unit = {},
    onHideBottomActionBar: () -> Unit = {},
    isBottomActionBarVisible: Boolean = false,
    onSwitchCategory: (FileCategory) -> Unit = {},
    onToggleAppSelection: (String) -> Unit,
    onToggleSystemApps: () -> Unit,
    onExportSelectedApps: () -> Unit,
    onRestoreTrashItem: (TrashItemEntity) -> Unit,
    onEmptyTrash: () -> Unit
) {
    var showSearchBar by remember { mutableStateOf(false) }
    var rubberBandRect by remember { mutableStateOf<Rect?>(null) }
    var listContainerRootOffset by remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    val hapticView = LocalView.current

    val itemRootBounds = remember { mutableStateMapOf<String, Rect>() }
    LaunchedEffect(state.currentPath, state.viewMode, state.mode) {
        itemRootBounds.clear()
        dragController.clearFolderBounds(state.paneIndex)
    }
    val filesByPath = remember(state.files) { state.files.associateBy { it.path } }

    val filteredFiles = remember(state.files, state.searchQuery) {
        val base = if (state.searchQuery.isBlank()) {
            state.files
        } else {
            state.files.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
        }
        base.sortedWith(
            compareByDescending<VfsFile> { it.isDirectory }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
        )
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val hasBottom3Selected = remember(filteredFiles, state.selectedPaths) {
        filteredFiles.takeLast(3).any { it.path in state.selectedPaths }
    }
    val bottomClearanceDp by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isBottomActionBarVisible || state.isMultiSelectMode || hasBottom3Selected) 112.dp else 72.dp,
        label = "BottomClearanceDp"
    )
    LaunchedEffect(isBottomActionBarVisible, hasBottom3Selected) {
        if (isBottomActionBarVisible && hasBottom3Selected) {
            val targetIdx = filteredFiles.indexOfLast { it.path in state.selectedPaths }
            if (targetIdx >= 0) {
                if (state.viewMode == FileViewMode.LIST) {
                    listState.animateScrollToItem(targetIdx)
                } else {
                    gridState.animateScrollToItem(targetIdx)
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onFocusPane),
        color = MaterialTheme.colorScheme.surface,
        border = if (isFocused) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                val folderCount = remember(filteredFiles) { filteredFiles.count { it.isDirectory } }
                val fileCount = remember(filteredFiles) { filteredFiles.count { !it.isDirectory } }
                val currentTitle = remember(state.currentPath, state.mountName, state.mode) {
                    if (state.mode == PaneMode.FILES) {
                        state.currentPath.substringAfterLast('/').ifBlank { state.mountName }
                    } else {
                        state.mountName
                    }
                }

                // 頂部圓角膠囊標題列（參照雙欄檔案管理器設計 + Windows 黃黑配色）
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = currentTitle,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$folderCount 個資料夾, $fileCount 個檔案",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { showSearchBar = !showSearchBar }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "搜尋",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            // 清單列表 / 小圖示 / 大圖示 檢視切換按鈕
                            IconButton(
                                onClick = { onViewModeChange(FileViewMode.LIST) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ViewList,
                                    contentDescription = "清單列表",
                                    tint = if (state.viewMode == FileViewMode.LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onViewModeChange(FileViewMode.SMALL_GRID) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.GridView,
                                    contentDescription = "小圖示",
                                    tint = if (state.viewMode == FileViewMode.SMALL_GRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onViewModeChange(FileViewMode.LARGE_GRID) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Apps,
                                    contentDescription = "大圖示",
                                    tint = if (state.viewMode == FileViewMode.LARGE_GRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (state.mode == PaneMode.FILES && hasClipboard) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .clickable {
                                            onFocusPane()
                                            onPasteClick()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentPaste,
                                            contentDescription = "貼上",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (clipboardCount > 1) "貼上($clipboardCount)" else "貼上",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            if (state.mode == PaneMode.FILES) {
                                IconButton(onClick = onCreateNewFolderClick, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Default.CreateNewFolder,
                                        contentDescription = "新增空白資料夾",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onAddBookmark(state.currentPath) }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Default.BookmarkAdd,
                                        contentDescription = "加入書籤",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                        // 右上角折疊按鈕固定在最右側（紅框處），窄螢幕絕不被擠出
                        IconButton(onClick = onCollapsePane, modifier = Modifier.size(34.dp)) {
                            Icon(
                                if (state.paneIndex == 3) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                                contentDescription = "摺疊此面板",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // 路徑麵包屑列
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isBackHovered = dragController.isDragging &&
                        dragController.hoveredBackButtonPane == state.paneIndex
                    val backHoverScale by animateFloatAsState(
                        targetValue = if (isBackHovered) 1.35f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "BackButtonHoverScale"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .scale(backHoverScale)
                            .then(
                                if (isBackHovered) {
                                    Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                            CircleShape
                                        )
                                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .onGloballyPositioned { coords ->
                                dragController.updateBackButtonBounds(state.paneIndex, coords.boundsInRoot())
                            }
                    ) {
                        IconButton(onClick = onNavigateUp, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "上一層",
                                tint = if (isBackHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        FolderMagnifierBadge(visible = isBackHovered)
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.mountName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        if (state.mode == PaneMode.FILES) {
                            val parts = state.currentPath.split('/').filter { it.isNotEmpty() }
                            var acc = ""
                            parts.forEach { seg ->
                                acc += "/$seg"
                                val target = acc
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = seg,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .clickable { onNavigateToPath(target) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                if (showSearchBar) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("搜尋目前目錄檔案...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // 媒體專區（「相片與影片」）子分類切換列：全部 / 照片 / 影片
                if (state.mode == PaneMode.CATEGORY &&
                    state.activeCategory in setOf(FileCategory.MEDIA, FileCategory.IMAGES, FileCategory.VIDEOS)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.activeCategory == FileCategory.MEDIA,
                            onClick = { onSwitchCategory(FileCategory.MEDIA) },
                            label = { Text("全部") }
                        )
                        FilterChip(
                            selected = state.activeCategory == FileCategory.IMAGES,
                            onClick = { onSwitchCategory(FileCategory.IMAGES) },
                            label = { Text("照片") }
                        )
                        FilterChip(
                            selected = state.activeCategory == FileCategory.VIDEOS,
                            onClick = { onSwitchCategory(FileCategory.VIDEOS) },
                            label = { Text("影片") }
                        )
                    }
                }

                // 進入選擇狀態時，於上方區域顯示「全選」勾選選項與取消鈕
                if ((state.isMultiSelectMode || state.selectedPaths.isNotEmpty()) &&
                    (state.mode == PaneMode.FILES || state.mode == PaneMode.CATEGORY)
                ) {
                    val allSelected = filteredFiles.isNotEmpty() &&
                        filteredFiles.all { it.path in state.selectedPaths }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                            .clickable {
                                onFocusPane()
                                if (allSelected) onClearSelection() else onSelectAll()
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { checked ->
                                    onFocusPane()
                                    if (checked) onSelectAll() else onClearSelection()
                                },
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "全選 (${state.selectedPaths.size}/${filteredFiles.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onClearSelection,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "取消選取",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider()

                // 主體內容區
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            listContainerRootOffset = coords.positionInRoot()
                        }
                        .dxMouseHandler(
                            onRightClick = { localPos ->
                                onFocusPane()
                                val rootPos = listContainerRootOffset + localPos
                                val hitPath = itemRootBounds.entries.firstOrNull { (_, r) -> r.contains(rootPos) }?.key
                                onShowContextMenu(hitPath?.let { filesByPath[it] })
                            },
                            onRubberBandStart = { onFocusPane() },
                            onRubberBandUpdate = { rect ->
                                rubberBandRect = rect
                                val rootRect = rect.translate(listContainerRootOffset)
                                val hits = itemRootBounds.entries
                                    .filter { (_, bounds) -> rootRect.overlaps(bounds) }
                                    .map { it.key }
                                    .toSet()
                                onRubberBandSelect(hits)
                            },
                            onRubberBandEnd = { rubberBandRect = null },
                            onCtrlWheelZoom = onCtrlWheelZoom,
                            onMouseBack = onNavigateBack,
                            onMouseForward = onNavigateForward
                        )
                        .dxFileTouchGesture(
                            context = context,
                            hapticView = hapticView,
                            findItemAtPosition = { localPos ->
                                val rootPos = listContainerRootOffset + localPos
                                itemRootBounds.entries.firstOrNull { (_, rect) -> rect.contains(rootPos) }?.key
                            },
                            onEmptySpaceLongPress = {
                                if (state.mode == PaneMode.FILES) {
                                    onFocusPane()
                                    onCreateNewFolderClick()
                                }
                            },
                            onLongPressEnterMultiSelect = { initialPath, localOffset ->
                                onFocusPane()
                                val file = filesByPath[initialPath] ?: return@dxFileTouchGesture
                                if (initialPath !in state.selectedPaths) {
                                    onToggleSelect(initialPath)
                                }
                                dragController.prepareHold(
                                    paneIndex = state.paneIndex,
                                    initialFile = file,
                                    rootOffset = listContainerRootOffset + localOffset
                                )
                                onShowBottomActionBar(false)
                            },
                            onSecondFingerTapItem = { tappedPath, localOffset ->
                                val file = filesByPath[tappedPath] ?: return@dxFileTouchGesture
                                onHideBottomActionBar()
                                if (tappedPath !in state.selectedPaths) {
                                    onToggleSelect(tappedPath)
                                }
                                dragController.addSecondFingerFile(
                                    file = file,
                                    tapRootOffset = listContainerRootOffset + localOffset
                                )
                            },
                            onSweepSelectItem = { sweptPath, localOffset ->
                                val file = filesByPath[sweptPath] ?: return@dxFileTouchGesture
                                if (sweptPath !in state.selectedPaths) {
                                    onToggleSelect(sweptPath)
                                }
                                dragController.addSecondFingerFile(
                                    file = file,
                                    tapRootOffset = listContainerRootOffset + localOffset
                                )
                            },
                            onPrimaryFingerDrag = { localOffset, hoveredPath ->
                                onHideBottomActionBar()
                                val rootOffset = listContainerRootOffset + localOffset
                                val isMultiWin = (context as? Activity)?.isInMultiWindowMode == true
                                val marginPx = if (isMultiWin) 24f else -4f
                                val w = hapticView.width.toFloat()
                                val h = hapticView.height.toFloat()
                                val crossingWindowEdge = w > 0f && h > 0f && (
                                    rootOffset.x <= marginPx ||
                                        rootOffset.x >= w - marginPx ||
                                        rootOffset.y <= marginPx ||
                                        rootOffset.y >= h - marginPx
                                    )
                                if (crossingWindowEdge &&
                                    dragController.draggedFiles.any { !it.isDirectory && it.mountId == "local" } &&
                                    SystemCrossAppDragHelper.startSystemDrag(
                                        view = hapticView,
                                        context = context,
                                        files = dragController.draggedFiles
                                    )
                                ) {
                                    dragController.resetDrag()
                                    return@dxFileTouchGesture
                                }
                                val hoveredFolder = hoveredPath?.takeIf { filesByPath[it]?.isDirectory == true }
                                dragController.updateDrag(
                                    rootOffset = rootOffset,
                                    hoveredFolder = hoveredFolder
                                )
                            },
                            onDropComplete = { _, targetFolderPath, didDrag ->
                                val validFolder = targetFolderPath?.takeIf { filesByPath[it]?.isDirectory == true }
                                dragController.finishDrag(
                                    fallbackPaneIndex = state.paneIndex,
                                    resolveTarget = resolvePaneTarget,
                                    targetFolderPath = validFolder,
                                    didDrag = didDrag
                                )
                                if (dragController.pendingDropTarget == null) {
                                    onShowBottomActionBar(true)
                                }
                            },
                            onStationaryRelease = { _, usedSecondFinger ->
                                dragController.resetDrag()
                                onShowBottomActionBar(usedSecondFinger)
                            },
                            onCancelDrag = {
                                dragController.resetDrag()
                            }
                        )
                ) {
                    when {
                        state.isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        state.errorMessage != null -> {
                            Text(
                                text = "載入錯誤：${state.errorMessage}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center).padding(16.dp)
                            )
                        }
                        state.mode == PaneMode.APPS -> {
                            AppManagerList(
                                apps = installedApps,
                                includeSystemApps = includeSystemApps,
                                selectedPackages = selectedAppPackages,
                                onToggleSystemApps = onToggleSystemApps,
                                onToggleSelect = onToggleAppSelection,
                                onExportSelected = onExportSelectedApps
                            )
                        }
                        state.mode == PaneMode.TRASH -> {
                            TrashBinList(
                                items = trashItems,
                                onRestore = onRestoreTrashItem,
                                onEmptyTrash = onEmptyTrash
                            )
                        }
                        filteredFiles.isEmpty() -> {
                            Text(
                                text = "此資料夾沒有檔案",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        else -> {
                            LaunchedEffect(state.currentPath) {
                                itemRootBounds.clear()
                                dragController.clearFolderBounds(state.paneIndex)
                            }
                            AnimatedContent(
                                targetState = state.currentPath to state.viewMode,
                                transitionSpec = {
                                    val oldPath = initialState.first
                                    val newPath = targetState.first
                                    if (oldPath == newPath) {
                                        fadeIn(tween(180)) togetherWith fadeOut(tween(150))
                                    } else {
                                        val isEnteringFolder = newPath.length >= oldPath.length
                                        if (isEnteringFolder) {
                                            // 進入資料夾：1 秒展開回彈特效
                                            (scaleIn(
                                                initialScale = 0.68f,
                                                animationSpec = spring(
                                                    dampingRatio = 0.60f,
                                                    stiffness = 65f
                                                )
                                            ) + fadeIn(tween(380))) togetherWith
                                                (scaleOut(
                                                    targetScale = 1.08f,
                                                    animationSpec = tween(900, easing = FastOutSlowInEasing)
                                                ) + fadeOut(tween(650)))
                                        } else {
                                            // 離開資料夾：1 秒收合回彈特效
                                            (scaleIn(
                                                initialScale = 1.12f,
                                                animationSpec = spring(
                                                    dampingRatio = 0.60f,
                                                    stiffness = 65f
                                                )
                                            ) + fadeIn(tween(380))) togetherWith
                                                (scaleOut(
                                                    targetScale = 0.68f,
                                                    animationSpec = tween(1000, easing = FastOutSlowInEasing)
                                                ) + fadeOut(tween(850)))
                                        }
                                    }
                                },
                                label = "FolderTransition"
                            ) { (_, animViewMode) ->
                                if (animViewMode != FileViewMode.LIST) {
                                    val cellMinSize = if (animViewMode == FileViewMode.LARGE_GRID) 132.dp else 88.dp
                                    val gridIconDp = if (animViewMode == FileViewMode.LARGE_GRID) 56.dp else 36.dp
                                    LazyVerticalGrid(
                                        state = gridState,
                                        columns = GridCells.Adaptive(minSize = cellMinSize),
                                        contentPadding = PaddingValues(
                                            start = 6.dp,
                                            end = 6.dp,
                                            top = 6.dp,
                                            bottom = bottomClearanceDp
                                        ),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .dxVerticalOverscrollBounce(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        gridItems(filteredFiles, key = { it.path }) { file ->
                                            val isSelected = file.path in state.selectedPaths
                                            val isHoveredTarget = dragController.isDragging &&
                                                file.isDirectory &&
                                                dragController.hoveredFolderPaneIndex == state.paneIndex &&
                                                dragController.hoveredFolderPath == file.path
                                            val hoverScale by animateFloatAsState(
                                                targetValue = if (isHoveredTarget) 1.15f else 1f,
                                                label = "GridFolderHoverScale"
                                            )
                                            FileGridItem(
                                                file = file,
                                                isSelected = isSelected,
                                                isHoveredTarget = isHoveredTarget,
                                                iconSizeDp = gridIconDp,
                                                modifier = Modifier
                                                    .scale(hoverScale)
                                                    .onGloballyPositioned { coords ->
                                                        val b = coords.boundsInRoot()
                                                        itemRootBounds[file.path] = b
                                                        if (file.isDirectory) {
                                                            dragController.updateFolderBounds(state.paneIndex, file.path, b)
                                                        }
                                                    }
                                                    .clickable {
                                                        if (dragController.isHolding || dragController.isDragging) return@clickable
                                                        onFocusPane()
                                                        if (state.isMultiSelectMode) {
                                                            onToggleSelect(file.path)
                                                        } else if (file.isDirectory) {
                                                            onNavigateToPath(file.path)
                                                        } else {
                                                            onOpenFile(file)
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        contentPadding = PaddingValues(bottom = bottomClearanceDp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .dxVerticalOverscrollBounce()
                                    ) {
                                        items(filteredFiles, key = { it.path }) { file ->
                                            val isSelected = file.path in state.selectedPaths
                                            val isHoveredTarget = dragController.isDragging &&
                                                file.isDirectory &&
                                                dragController.hoveredFolderPaneIndex == state.paneIndex &&
                                                dragController.hoveredFolderPath == file.path
                                            val hoverScale by animateFloatAsState(
                                                targetValue = if (isHoveredTarget) 1.15f else 1f,
                                                label = "FolderHoverScale"
                                            )

                                            FileRowItem(
                                                file = file,
                                                isSelected = isSelected,
                                                isMultiSelectMode = state.isMultiSelectMode,
                                                isHoveredTarget = isHoveredTarget,
                                                iconScale = state.iconScale,
                                                modifier = Modifier
                                                    .scale(hoverScale)
                                                    .onGloballyPositioned { coords ->
                                                        val b = coords.boundsInRoot()
                                                        itemRootBounds[file.path] = b
                                                        if (file.isDirectory) {
                                                            dragController.updateFolderBounds(state.paneIndex, file.path, b)
                                                        }
                                                    }
                                                    .clickable {
                                                        if (dragController.isHolding || dragController.isDragging) return@clickable
                                                        onFocusPane()
                                                        if (state.isMultiSelectMode) {
                                                            onToggleSelect(file.path)
                                                        } else if (file.isDirectory) {
                                                            onNavigateToPath(file.path)
                                                        } else {
                                                            onOpenFile(file)
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 滑鼠拉框選取視覺層
                    RubberBandOverlay(rubberBandRect)
                }
            }

            if (state.mode == PaneMode.FILES) {
                FloatingActionButton(
                    onClick = {
                        onFocusPane()
                        onCreateNewFolderClick()
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .size(50.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新增資料夾")
                }
            }
        }
    }
}

@Composable
private fun FileGridItem(
    file: VfsFile,
    isSelected: Boolean,
    isHoveredTarget: Boolean,
    iconSizeDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isHoveredTarget -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(10.dp))
            .then(
                if (isHoveredTarget) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                else Modifier
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (file.isDirectory) {
            Box(contentAlignment = Alignment.Center) {
                IosGlassFolderIcon(
                    isEmpty = file.isEmpty,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(iconSizeDp)
                )
                FolderMagnifierBadge(visible = isHoveredTarget)
            }
        } else {
            FileThumbnailIcon(
                file = file,
                iconSize = iconSizeDp,
                cornerRadius = 10.dp
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FileRowItem(
    file: VfsFile,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    isHoveredTarget: Boolean,
    iconScale: Float,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isHoveredTarget -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    val timeText = remember(file.lastModified) {
        SimpleDateFormat("yyyy年M月d日 a h:mm:ss", Locale.getDefault()).format(Date(file.lastModified))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .background(bgColor, RoundedCornerShape(10.dp))
            .then(
                if (isHoveredTarget) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode || isSelected) {
            Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .size((40 * iconScale).dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (file.isDirectory) {
                IosGlassFolderIcon(
                    isEmpty = file.isEmpty,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size((28 * iconScale).dp)
                )
                FolderMagnifierBadge(visible = isHoveredTarget)
            } else {
                FileThumbnailIcon(
                    file = file,
                    iconSize = (36 * iconScale).dp,
                    cornerRadius = 8.dp
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (file.isDirectory) "資料夾" else formatFileSize(file.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FolderMagnifierBadge(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            shadowElevation = 6.dp,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "資料夾放大預覽",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun AppManagerList(
    apps: List<InstalledAppInfo>,
    includeSystemApps: Boolean,
    selectedPackages: Set<String>,
    onToggleSystemApps: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onExportSelected: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = includeSystemApps,
                onClick = onToggleSystemApps,
                label = { Text("顯示系統應用") }
            )
            Button(
                enabled = selectedPackages.isNotEmpty(),
                onClick = onExportSelected
            ) {
                Text("匯出 APK (${selectedPackages.size})")
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { app ->
                val checked = app.packageName in selectedPackages
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSelect(app.packageName) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { onToggleSelect(app.packageName) })
                    Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${app.packageName} • v${app.versionName} • ${formatFileSize(app.apkSizeBytes)}${if (app.hasSplitApks) " (Split)" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashBinList(
    items: List<TrashItemEntity>,
    onRestore: (TrashItemEntity) -> Unit,
    onEmptyTrash: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("垃圾桶項目（超過 30 天自動清除）", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(enabled = items.isNotEmpty(), onClick = onEmptyTrash) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("清空")
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.originalName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "原路徑：${item.originalPath} • ${formatFileSize(item.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRestore(item) }) {
                        Icon(Icons.Default.RestoreFromTrash, contentDescription = "還原", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
