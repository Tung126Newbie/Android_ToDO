package com.example.simplenotes.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.simplenotes.data.repository.AppLanguage
import com.example.simplenotes.data.repository.AppTheme
import com.example.simplenotes.domain.model.Note
import com.example.simplenotes.presentation.viewmodel.NoteViewModel
import com.example.simplenotes.presentation.viewmodel.SettingsViewModel
import com.example.simplenotes.presentation.viewmodel.SortType
import com.example.simplenotes.util.DateUtils
import com.example.simplenotes.util.StringUtils
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NoteListScreen(
    navController: NavController,
    viewModel: NoteViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val filteredNotes by viewModel.filteredNotes.collectAsStateWithLifecycle()
    val sortType by viewModel.sortType.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val appTheme by settingsViewModel.appTheme.collectAsStateWithLifecycle()
    val appLanguage by settingsViewModel.appLanguage.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    
    var showSortMenu by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    
    // State cho kéo thả
    var draggedNote by remember { mutableStateOf<Note?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) } 
    var touchOffset by remember { mutableStateOf(Offset.Zero) } 
    var draggedNoteSize by remember { mutableStateOf(IntSize.Zero) }
    
    var trashBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var isOverTrash by remember { mutableStateOf(false) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    
    var lastSwapTime by remember { mutableLongStateOf(0L) }
    val itemsBounds = remember { mutableStateMapOf<Long, androidx.compose.ui.geometry.Rect>() }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = appTheme,
            onThemeSelected = { 
                settingsViewModel.setTheme(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = appLanguage,
            onLanguageSelected = { 
                settingsViewModel.setLanguage(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootPosition = it.positionInRoot() }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text(if (appLanguage == AppLanguage.VIETNAMESE) "Tìm kiếm ghi chú..." else "Search notes...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    }
                                }
                            },
                            singleLine = true
                        )
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (appLanguage == AppLanguage.VIETNAMESE) "Tùy chỉnh" else "Custom") },
                                    onClick = { viewModel.setSortType(SortType.CUSTOM); showSortMenu = false },
                                    leadingIcon = { if (sortType == SortType.CUSTOM) Icon(Icons.Default.Check, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (appLanguage == AppLanguage.VIETNAMESE) "Mới nhất" else "Newest") },
                                    onClick = { viewModel.setSortType(SortType.CREATED_NEWEST); showSortMenu = false },
                                    leadingIcon = { if (sortType == SortType.CREATED_NEWEST) Icon(Icons.Default.Check, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("A-Z") },
                                    onClick = { viewModel.setSortType(SortType.A_Z); showSortMenu = false },
                                    leadingIcon = { if (sortType == SortType.A_Z) Icon(Icons.Default.Check, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (appLanguage == AppLanguage.VIETNAMESE) "Báo thức gần nhất" else "Reminder soonest") },
                                    onClick = { viewModel.setSortType(SortType.REMINDER_SOON); showSortMenu = false },
                                    leadingIcon = { if (sortType == SortType.REMINDER_SOON) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }
                        
                        Box {
                            IconButton(onClick = { showSettingsMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                            }
                            DropdownMenu(
                                expanded = showSettingsMenu,
                                onDismissRequest = { showSettingsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (appLanguage == AppLanguage.VIETNAMESE) "Chủ đề" else "Theme") },
                                    onClick = { showThemeDialog = true; showSettingsMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Palette, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (appLanguage == AppLanguage.VIETNAMESE) "Ngôn ngữ" else "Language") },
                                    onClick = { showLanguageDialog = true; showSettingsMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Language, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (appLanguage == AppLanguage.VIETNAMESE) "Thùng rác" else "Trash") },
                                    onClick = { navController.navigate("trash"); showSettingsMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !isDragging,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val secondaryColor = MaterialTheme.colorScheme.secondary
                    
                    Box(
                        modifier = Modifier
                            .height(56.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(20.dp),
                                ambientColor = primaryColor.copy(alpha = 0.5f),
                                spotColor = primaryColor
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(primaryColor, secondaryColor)
                                )
                            )
                            .clickable {
                                viewModel.clearCurrentNote()
                                navController.navigate("edit")
                            }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add, 
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (appLanguage == AppLanguage.VIETNAMESE) "Tạo mới" else "Create",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (filteredNotes.isEmpty()) {
                    EmptyStateView(
                        modifier = Modifier.align(Alignment.Center),
                        isVietnamese = appLanguage == AppLanguage.VIETNAMESE
                    )
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !isDragging,
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 12.dp
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            var itemPosition by remember { mutableStateOf(Offset.Zero) }
                            var itemSize by remember { mutableStateOf(IntSize.Zero) }
                            val isBeingDragged = draggedNote?.id == note.id
                            
                            NoteItem(
                                note = note,
                                modifier = Modifier
                                    .animateItem()
                                    .graphicsLayer { alpha = if (isBeingDragged) 0f else 1f }
                                    .onGloballyPositioned { 
                                        itemPosition = it.positionInRoot() 
                                        itemSize = it.size
                                        itemsBounds[note.id] = it.boundsInRoot()
                                    }
                                    .pointerInput(note.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { offset ->
                                                draggedNote = note
                                                isDragging = true
                                                touchOffset = offset
                                                dragOffset = itemPosition + offset
                                                draggedNoteSize = itemSize
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount
                                                
                                                val wasOverTrash = isOverTrash
                                                isOverTrash = trashBounds.contains(dragOffset)
                                                
                                                if (isOverTrash && !wasOverTrash) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                                
                                                val nearTrashZone = dragOffset.y > (trashBounds.top - 100)
                                                if (!isOverTrash && !nearTrashZone && sortType == SortType.CUSTOM) {
                                                    val currentTime = System.currentTimeMillis()
                                                    if (currentTime - lastSwapTime > 300) {
                                                        itemsBounds.forEach { (id, bounds) ->
                                                            if (id != note.id && bounds.contains(dragOffset)) {
                                                                viewModel.swapNotes(note.id, id)
                                                                lastSwapTime = currentTime
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                if (isOverTrash && draggedNote != null) {
                                                    viewModel.deleteNote(draggedNote!!)
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                                isDragging = false
                                                draggedNote = null
                                                isOverTrash = false
                                            },
                                            onDragCancel = {
                                                isDragging = false
                                                draggedNote = null
                                                isOverTrash = false
                                            }
                                        )
                                    },
                                onClick = {
                                    viewModel.setCurrentNote(note)
                                    navController.navigate("edit/${note.id}")
                                }
                            )
                        }
                    }
                }

                // Delete Zone
                AnimatedVisibility(
                    visible = isDragging,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isOverTrash) MaterialTheme.colorScheme.error 
                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                            )
                            .onGloballyPositioned { 
                                trashBounds = it.boundsInRoot()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isOverTrash) Icons.Default.DeleteForever else Icons.Default.Delete,
                                contentDescription = null,
                                tint = if (isOverTrash) Color.White else MaterialTheme.colorScheme.error,
                                modifier = Modifier.scale(if (isOverTrash) 1.2f else 1.0f)
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.VIETNAMESE) "Kéo vào đây để xóa" else "Drag here to delete",
                                color = if (isOverTrash) Color.White else MaterialTheme.colorScheme.error,
                                fontWeight = if (isOverTrash) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Dragging item overlay
        if (isDragging && draggedNote != null) {
            Box(
                modifier = Modifier
                    .offset { 
                        IntOffset(
                            (dragOffset.x - touchOffset.x - rootPosition.x).roundToInt(),
                            (dragOffset.y - touchOffset.y - rootPosition.y).roundToInt()
                        ) 
                    }
                    .width(with(LocalDensity.current) { draggedNoteSize.width.toDp() })
                    .height(with(LocalDensity.current) { draggedNoteSize.height.toDp() })
                    .zIndex(100f) 
                    .graphicsLayer {
                        val isTrash = isOverTrash
                        alpha = if (isTrash) 0.7f else 0.95f
                        scaleX = if (isTrash) 0.85f else 1.1f
                        scaleY = if (isTrash) 0.85f else 1.1f
                        rotationZ = if (isTrash) 0f else 4f
                        
                        shadowElevation = 12.dp.toPx()
                        shape = RoundedCornerShape(20.dp)
                        clip = false 
                    }
            ) {
                NoteItem(
                    note = draggedNote!!,
                    elevation = 0.dp, 
                    onClick = {}
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(modifier: Modifier = Modifier, isVietnamese: Boolean = true) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.NoteAdd,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isVietnamese) "Chưa có ghi chú nào" else "No notes yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn chủ đề") },
        text = {
            Column {
                ThemeOption(
                    title = "Sáng",
                    selected = currentTheme == AppTheme.LIGHT,
                    onClick = { onThemeSelected(AppTheme.LIGHT) },
                    icon = Icons.Default.LightMode
                )
                ThemeOption(
                    title = "Tối",
                    selected = currentTheme == AppTheme.DARK,
                    onClick = { onThemeSelected(AppTheme.DARK) },
                    icon = Icons.Default.DarkMode
                )
                ThemeOption(
                    title = "Hệ thống",
                    selected = currentTheme == AppTheme.SYSTEM,
                    onClick = { onThemeSelected(AppTheme.SYSTEM) },
                    icon = Icons.Outlined.Settings
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@Composable
fun LanguageSelectionDialog(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn ngôn ngữ") },
        text = {
            Column {
                ThemeOption(
                    title = "English",
                    selected = currentLanguage == AppLanguage.ENGLISH,
                    onClick = { onLanguageSelected(AppLanguage.ENGLISH) },
                    icon = Icons.Default.Language
                )
                ThemeOption(
                    title = "Tiếng Việt",
                    selected = currentLanguage == AppLanguage.VIETNAMESE,
                    onClick = { onLanguageSelected(AppLanguage.VIETNAMESE) },
                    icon = Icons.Default.Language
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@Composable
fun ThemeOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.weight(1f) )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteItem(
    note: Note, 
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 2.dp,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (note.title.isNotBlank()) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (note.content.isNotBlank()) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Hiển thị TẤT CẢ các báo thức đã đặt
            if (note.reminders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    note.reminders.sortedBy { it.time }.forEach { reminder ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = DateUtils.formatReminderTime(reminder.time),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
