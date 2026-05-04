package com.example.simplenotes.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.simplenotes.data.repository.AppLanguage
import com.example.simplenotes.data.repository.AppTheme
import com.example.simplenotes.domain.model.Note
import com.example.simplenotes.presentation.viewmodel.NoteViewModel
import com.example.simplenotes.presentation.viewmodel.SettingsViewModel
import com.example.simplenotes.presentation.viewmodel.SortType
import com.example.simplenotes.util.DateUtils
import kotlinx.coroutines.launch
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
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    
    val isDark = when(appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    // Kiểm tra bàn phím có đang hiện hay không
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    
    // State cho hiệu ứng cuộn đàn hồi
    val offsetY = remember { Animatable(0f) }
    
    var showMainMenu by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    
    var isSearchFocused by remember { mutableStateOf(false) }
    
    // Nút sẽ ở trạng thái "Compact" (tròn, nhỏ, nhích lên) CHỈ KHI bàn phím đang hiện hoặc đang focus
    val isFabCompact = isKeyboardVisible || isSearchFocused
    
    // Tự động xóa focus khi bàn phím đóng
    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            focusManager.clearFocus()
        }
    }

    // Animations cho FAB - Sử dụng chung một spec để đồng bộ
    val fabAnimationSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val fabDpAnimationSpec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val fabScale by animateFloatAsState(
        targetValue = if (isFabCompact) 1.1f else 1f,
        animationSpec = fabAnimationSpec,
        label = "fabScale"
    )
    val fabVerticalOffset by animateDpAsState(
        targetValue = if (isFabCompact) (-12).dp else 0.dp,
        animationSpec = fabDpAnimationSpec,
        label = "fabOffset"
    )
    val fabHorizontalPadding by animateDpAsState(
        targetValue = if (isFabCompact) 16.dp else 20.dp,
        animationSpec = fabDpAnimationSpec,
        label = "fabHorizontalPadding"
    )
    val fabCornerRadius by animateDpAsState(
        targetValue = if (isFabCompact) 28.dp else 20.dp,
        animationSpec = fabDpAnimationSpec,
        label = "fabCorner"
    )

    // State cho kéo thả item
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

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.Drag && isSearchFocused) {
                    focusManager.clearFocus()
                }
                
                if (!isDragging && available.y != 0f && source == NestedScrollSource.Drag) {
                    scope.launch {
                        offsetY.snapTo(offsetY.value + available.y * 0.2f)
                    }
                }
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isDragging && source == NestedScrollSource.Drag) {
                    if (offsetY.value > 0 && available.y < 0) {
                        val newOffset = (offsetY.value + available.y).coerceAtLeast(0f)
                        scope.launch { offsetY.snapTo(newOffset) }
                        return Offset(0f, available.y)
                    }
                    if (offsetY.value < 0 && available.y > 0) {
                        val newOffset = (offsetY.value + available.y).coerceAtMost(0f)
                        scope.launch { offsetY.snapTo(newOffset) }
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                scope.launch {
                    offsetY.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

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
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    color = if (isDark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shadowElevation = if (isSearchFocused) 8.dp else 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thanh Tìm kiếm (Search Bar) - Co giãn tự động
                        val searchBgColor = if (isDark) 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else 
                            Color.White
                        
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { 
                                Text(
                                    text = if (appLanguage == AppLanguage.VIETNAMESE) "Tìm kiếm ghi chú..." else "Search notes...",
                                    style = MaterialTheme.typography.bodyLarge
                                ) 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(searchBgColor)
                                .onFocusChanged { isSearchFocused = it.isFocused }
                                .border(
                                    width = 1.dp,
                                    color = if (isSearchFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(24.dp)
                                ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = searchBgColor,
                                unfocusedContainerColor = searchBgColor,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            leadingIcon = { 
                                Icon(
                                    imageVector = Icons.Default.Search, 
                                    contentDescription = null,
                                    tint = if (isSearchFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ) 
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty() || isSearchFocused) {
                                    IconButton(onClick = { 
                                        viewModel.setSearchQuery("") 
                                        focusManager.clearFocus()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Close, 
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Nút Menu tổng hợp duy nhất
                        Box {
                            IconButton(onClick = { showMainMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert, 
                                    contentDescription = "Menu",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showMainMenu,
                                onDismissRequest = { showMainMenu = false }
                            ) {
                                // Section: Sắp xếp
                                Text(
                                    text = if (appLanguage == AppLanguage.VIETNAMESE) "  Sắp xếp theo" else "  Sort by",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                                )
                                DropdownMenuItem(
                                    text = { Text(if (appLanguage == AppLanguage.VIETNAMESE) "Tùy chỉnh" else "Custom") },
                                    onClick = { viewModel.setSortType(SortType.CUSTOM); showMainMenu = false },
                                    leadingIcon = { if (sortType == SortType.CUSTOM) Icon(Icons.Default.Check, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (appLanguage == AppLanguage.VIETNAMESE) "Mới nhất" else "Newest") },
                                    onClick = { viewModel.setSortType(SortType.CREATED_NEWEST); showMainMenu = false },
                                    leadingIcon = { if (sortType == SortType.CREATED_NEWEST) Icon(Icons.Default.Check, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("A-Z") },
                                    onClick = { viewModel.setSortType(SortType.A_Z); showMainMenu = false },
                                    leadingIcon = { if (sortType == SortType.A_Z) Icon(Icons.Default.Check, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (appLanguage == AppLanguage.VIETNAMESE) "Báo thức gần nhất" else "Reminder soonest") },
                                    onClick = { viewModel.setSortType(SortType.REMINDER_SOON); showMainMenu = false },
                                    leadingIcon = { if (sortType == SortType.REMINDER_SOON) Icon(Icons.Default.Check, null) }
                                )
                                
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                // Section: Cài đặt & Tùy chọn
                                Text(
                                    text = if (appLanguage == AppLanguage.VIETNAMESE) "  Ứng dụng" else "  App settings",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                                )
                                DropdownMenuItem(
                                    text = { Text(if (appLanguage == AppLanguage.VIETNAMESE) "Chủ đề" else "Theme") },
                                    onClick = { showThemeDialog = true; showMainMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Palette, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (appLanguage == AppLanguage.VIETNAMESE) "Ngôn ngữ" else "Language") },
                                    onClick = { showLanguageDialog = true; showMainMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Language, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (appLanguage == AppLanguage.VIETNAMESE) "Thùng rác" else "Trash") },
                                    onClick = { navController.navigate("trash"); showMainMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                                )
                            }
                        }
                    }
                }
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
                            .imePadding()
                            .padding(bottom = 32.dp, end = 16.dp)
                            .offset(y = fabVerticalOffset)
                            .graphicsLayer {
                                scaleX = fabScale
                                scaleY = fabScale
                            }
                            .height(56.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(fabCornerRadius),
                                ambientColor = primaryColor.copy(alpha = 0.5f),
                                spotColor = primaryColor
                            )
                            .clip(RoundedCornerShape(fabCornerRadius))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(primaryColor, secondaryColor)
                                )
                            )
                            .clickable {
                                focusManager.clearFocus()
                                viewModel.clearCurrentNote()
                                navController.navigate("edit")
                            }
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = fabHorizontalPadding)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add, 
                                contentDescription = null,
                                tint = Color.White
                            )
                            AnimatedVisibility(
                                visible = !isFabCompact,
                                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.VIETNAMESE) "Tạo mới" else "Create",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(nestedScrollConnection)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val contentModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = offsetY.value
                    }

                if (filteredNotes.isEmpty()) {
                    EmptyStateView(
                        modifier = contentModifier.align(Alignment.Center),
                        isVietnamese = appLanguage == AppLanguage.VIETNAMESE
                    )
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = contentModifier,
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
                                                focusManager.clearFocus()
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
                                    focusManager.clearFocus()
                                    viewModel.setCurrentNote(note)
                                    navController.navigate("edit/${note.id}")
                                }
                            )
                        }
                    }
                }

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
    // Trích xuất hình ảnh đầu tiên từ nội dung nếu có
    val firstImageUri = remember(note.content) {
        val regex = Regex("\\[Hình ảnh: (.*?)(?:\\|(.*?))?\\]")
        regex.find(note.content)?.groups?.get(1)?.value
    }

    // Làm sạch nội dung (bỏ các tag hình ảnh) để hiển thị text
    val cleanContent = remember(note.content) {
        note.content.replace(Regex("\\[Hình ảnh: .*?\\]"), "").trim()
    }

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
        Column {
            // Hiển thị ảnh ở trên cùng nếu có
            if (firstImageUri != null) {
                AsyncImage(
                    model = android.net.Uri.parse(firstImageUri),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                    contentScale = ContentScale.Crop // Crop center tự động
                )
            }

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
                
                if (cleanContent.isNotBlank()) {
                    Text(
                        text = cleanContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = if (firstImageUri != null) 3 else 8, // Giảm dòng nếu có ảnh
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
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
}
