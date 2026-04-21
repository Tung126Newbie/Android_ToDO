package com.example.simplenotes.presentation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.simplenotes.data.repository.AppLanguage
import com.example.simplenotes.domain.model.Note
import com.example.simplenotes.presentation.viewmodel.NoteViewModel
import com.example.simplenotes.presentation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    navController: NavController,
    viewModel: NoteViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val deletedNotes by viewModel.deletedNotes.collectAsStateWithLifecycle()
    val appLanguage by settingsViewModel.appLanguage.collectAsStateWithLifecycle()
    val isVietnamese = appLanguage == AppLanguage.VIETNAMESE
    
    var noteToDeletePermanently by remember { mutableStateOf<Note?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    val hasDeletedNotes by remember { derivedStateOf { deletedNotes.isNotEmpty() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isVietnamese) "Thùng rác" else "Trash Bin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (hasDeletedNotes) {
                        IconButton(onClick = { showClearAllConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (!hasDeletedNotes) {
                EmptyTrashView(
                    modifier = Modifier.align(Alignment.Center),
                    isVietnamese = isVietnamese
                )
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp
                ) {
                    items(
                        items = deletedNotes, 
                        key = { it.id }
                    ) { note ->
                        TrashItem(
                            note = note,
                            onRestore = { viewModel.restoreNote(note) },
                            onDeletePermanently = { noteToDeletePermanently = note },
                            isVietnamese = isVietnamese,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    // Dialog xác nhận xóa một ghi chú
    if (noteToDeletePermanently != null) {
        AlertDialog(
            onDismissRequest = { noteToDeletePermanently = null },
            title = { Text(if (isVietnamese) "Xóa vĩnh viễn?" else "Delete permanently?") },
            text = { Text(if (isVietnamese) "Ghi chú này sẽ bị xóa hoàn toàn khỏi thiết bị." else "This note will be completely removed from the device.") },
            confirmButton = {
                Button(
                    onClick = {
                        noteToDeletePermanently?.let { viewModel.permanentlyDeleteNote(it) }
                        noteToDeletePermanently = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (isVietnamese) "Xóa" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { noteToDeletePermanently = null }) { 
                    Text(if (isVietnamese) "Hủy" else "Cancel") 
                }
            }
        )
    }

    // Dialog xác nhận dọn sạch thùng rác
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(if (isVietnamese) "Dọn sạch thùng rác?" else "Empty trash?") },
            text = { Text(if (isVietnamese) "Tất cả ghi chú trong thùng rác sẽ bị xóa vĩnh viễn. Hành động này không thể hoàn tác." else "All notes in the trash will be permanently deleted. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearTrash()
                        showClearAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (isVietnamese) "Tôi chắc chắn" else "I'm sure") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { 
                    Text(if (isVietnamese) "Hủy" else "Cancel") 
                }
            }
        )
    }
}

@Composable
fun EmptyTrashView(modifier: Modifier = Modifier, isVietnamese: Boolean) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🗑️", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isVietnamese) "Thùng rác trống" else "Trash bin is empty",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun TrashItem(
    note: Note,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    isVietnamese: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = note.title.ifBlank { if (isVietnamese) "(Không có tiêu đề)" else "(No title)" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onRestore,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDeletePermanently,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
