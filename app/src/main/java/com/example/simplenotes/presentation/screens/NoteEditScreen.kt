package com.example.simplenotes.presentation.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.simplenotes.data.repository.AppLanguage
import com.example.simplenotes.domain.model.Reminder
import com.example.simplenotes.presentation.viewmodel.NoteViewModel
import com.example.simplenotes.presentation.viewmodel.SettingsViewModel
import com.example.simplenotes.util.DateUtils
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    navController: NavController,
    noteId: Long?,
    viewModel: NoteViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentNote by viewModel.currentNote.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val appLanguage by settingsViewModel.appLanguage.collectAsState()
    val isVietnamese = appLanguage == AppLanguage.VIETNAMESE

    var titleValue by remember { mutableStateOf(TextFieldValue("")) }
    var contentValue by remember { mutableStateOf(TextFieldValue("")) }

    var reminders by remember { mutableStateOf<List<Reminder>>(emptyList()) }
    var isInitialLoadDone by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()

    var pendingTime by remember { mutableLongStateOf(0L) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAiConfirm by remember { mutableStateOf(false) }
    var showImageSourceOptions by remember { mutableStateOf(false) }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    var tempImageUri by rememberSaveable { mutableStateOf<String?>(null) }

    val noteTransformation = remember {
        VisualTransformation { text ->
            val builder = AnnotatedString.Builder()
            val regex = Regex("\\[Hình ảnh: (.*?)\\]")
            var lastIndex = 0
            val mapping = mutableListOf<Pair<Int, Int>>()
            var currentOffset = 0

            regex.findAll(text).forEach { match ->
                if (match.range.first > lastIndex) {
                    val part = text.substring(lastIndex, match.range.first)
                    builder.append(part)
                    for (i in part.indices) {
                        mapping.add((lastIndex + i) to (currentOffset + i))
                    }
                    currentOffset += part.length
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < text.length) {
                val part = text.substring(lastIndex)
                builder.append(part)
                for (i in part.indices) {
                    mapping.add((lastIndex + i) to (currentOffset + i))
                }
            }

            val builtString = builder.toAnnotatedString()
            TransformedText(builtString, object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    return mapping.find { it.first == offset }?.second
                        ?: mapping.lastOrNull { it.first <= offset }?.second?.plus(1)
                        ?: builtString.length
                }
                override fun transformedToOriginal(offset: Int): Int {
                    return mapping.find { it.second == offset }?.first
                        ?: mapping.lastOrNull { it.second <= offset }?.first?.plus(1)
                        ?: text.length
                }
            })
        }
    }

    fun createTempPictureUri(): Uri? {
        return try {
            val file = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val imageTag = "\n[Hình ảnh: $it]\n"
            contentValue = TextFieldValue(contentValue.text + imageTag, TextRange(contentValue.text.length + imageTag.length))
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempImageUri != null) {
            val imageTag = "\n[Hình ảnh: $tempImageUri]\n"
            contentValue = TextFieldValue(contentValue.text + imageTag, TextRange(contentValue.text.length + imageTag.length))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            val uri = createTempPictureUri()
            tempImageUri = uri?.toString()
            uri?.let { cameraLauncher.launch(it) }
        }
    }

    LaunchedEffect(noteId) {
        if (noteId != null && noteId != 0L) {
            viewModel.loadNoteById(noteId)
        }
    }

    LaunchedEffect(currentNote) {
        if (currentNote != null && !isInitialLoadDone) {
            titleValue = TextFieldValue(currentNote!!.title)
            contentValue = TextFieldValue(currentNote!!.content)
            reminders = currentNote!!.reminders
            isInitialLoadDone = true
        }
    }

    val hasChanges by remember {
        derivedStateOf {
            titleValue.text != (currentNote?.title ?: "") ||
                    contentValue.text != (currentNote?.content ?: "") ||
                    reminders != (currentNote?.reminders ?: emptyList<Reminder>())
        }
    }

    fun saveNote() {
        if (!hasChanges) { navController.popBackStack(); return }
        viewModel.insertOrUpdateNote(titleValue.text, contentValue.text, reminders) {
            if (it) Toast.makeText(context, if (isVietnamese) "Đã lưu" else "Saved", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
        }
    }

    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false; editingReminder = null },
            confirmButton = { TextButton(onClick = { showDatePicker = false; showTimePicker = true }) { Text(if (isVietnamese) "Tiếp" else "Next") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false; editingReminder = null }) { Text(if (isVietnamese) "Hủy" else "Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        AlertDialog(onDismissRequest = { showTimePicker = false; editingReminder = null },
            confirmButton = {
                TextButton(onClick = {
                    val date = Instant.ofEpochMilli(datePickerState.selectedDateMillis ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDate()
                    val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    pendingTime = ZonedDateTime.of(date, time, ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if (pendingTime > System.currentTimeMillis()) { showTimePicker = false; showTypePicker = true }
                    else Toast.makeText(context, if (isVietnamese) "Thời gian không hợp lệ!" else "Invalid time!", Toast.LENGTH_SHORT).show()
                }) { Text(if (isVietnamese) "Tiếp" else "Next") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false; editingReminder = null }) { Text(if (isVietnamese) "Lùi" else "Back") } },
            text = { Column { Text(if (isVietnamese) "Giờ báo thức" else "Alarm time"); TimePicker(state = timePickerState) } }
        )
    }

    if (showTypePicker) {
        AlertDialog(onDismissRequest = { showTypePicker = false; editingReminder = null },
            title = { Text(if (editingReminder != null) (if (isVietnamese) "Sửa báo thức" else "Edit reminder") else (if (isVietnamese) "Thêm báo thức" else "Add reminder")) },
            confirmButton = { 
                TextButton(onClick = { 
                    val newReminder = Reminder(editingReminder?.id ?: reminders.size, pendingTime, true)
                    if (editingReminder != null) {
                        reminders = reminders.map { if (it.id == editingReminder!!.id) newReminder else it }.sortedBy { it.time }
                    } else {
                        reminders = (reminders + newReminder).sortedBy { it.time }
                    }
                    showTypePicker = false
                    editingReminder = null
                }) { Text(if (isVietnamese) "Hằng tuần" else "Weekly") }
            },
            dismissButton = { 
                TextButton(onClick = { 
                    val newReminder = Reminder(editingReminder?.id ?: reminders.size, pendingTime, false)
                    if (editingReminder != null) {
                        reminders = reminders.map { if (it.id == editingReminder!!.id) newReminder else it }.sortedBy { it.time }
                    } else {
                        reminders = (reminders + newReminder).sortedBy { it.time }
                    }
                    showTypePicker = false
                    editingReminder = null
                }) { Text(if (isVietnamese) "Một lần" else "Once") } 
            }
        )
    }

    if (showImageSourceOptions) {
        ModalBottomSheet(onDismissRequest = { showImageSourceOptions = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                ListItem(
                    headlineContent = { Text(if (isVietnamese) "Chụp ảnh" else "Take photo") }, 
                    leadingContent = { Icon(Icons.Default.PhotoCamera, null, Modifier.size(28.dp)) }, 
                    modifier = Modifier.clickable { 
                        showImageSourceOptions = false
                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                    }
                )
                ListItem(
                    headlineContent = { Text(if (isVietnamese) "Thư viện ảnh" else "Gallery") },
                    leadingContent = { Icon(Icons.Default.Add, null, Modifier.size(28.dp)) }, 
                    modifier = Modifier.clickable { 
                        showImageSourceOptions = false
                        galleryLauncher.launch("image/*")
                    }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = { saveNote() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (isAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { showAiConfirm = true }) {
                            Icon(Icons.Outlined.AutoAwesome, "AI Magic", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { editingReminder = null; showDatePicker = true }) { Box { Icon(Icons.Outlined.AccessTime, null); Icon(Icons.Default.Add, null, Modifier.size(12.dp).align(Alignment.BottomEnd), MaterialTheme.colorScheme.primary) } }
                    if (hasChanges) IconButton(onClick = { saveNote() }) { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                }
            )
        },
        bottomBar = {
            BottomAppBar(tonalElevation = 8.dp) {
                IconButton(onClick = {
                    val text = contentValue.text
                    val start = contentValue.selection.start
                    val lineStart = if (text.lastIndexOf('\n', start - 1) == -1) 0 else text.lastIndexOf('\n', start - 1) + 1
                    val lineText = text.substring(lineStart)
                    val prefix = "☐ "
                    val (newText, newSelection) = when {
                        lineText.startsWith(prefix) -> text.removeRange(lineStart, lineStart + 2) to (start - 2).coerceAtLeast(0)
                        lineText.startsWith("☑ ") -> text.replaceRange(lineStart, lineStart + 2, prefix) to start
                        else -> text.replaceRange(lineStart, lineStart, prefix) to (start + 2)
                    }
                    contentValue = TextFieldValue(newText, TextRange(newSelection))
                }) { Icon(Icons.Default.CheckBox, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { showImageSourceOptions = true }) { Icon(Icons.Default.PhotoCamera, null, Modifier.size(32.dp)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.clearCurrentNote(); navController.navigate("edit") }) { Icon(Icons.Default.Add, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary) }
                if (noteId != null && noteId != 0L) IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.DeleteOutline, null, Modifier.size(32.dp)) }
            }
        }
    ) { pv ->
        Column(Modifier.fillMaxSize().padding(pv).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)) {
            if (reminders.isNotEmpty()) {
                Text(if (isVietnamese) "Báo thức:" else "Reminders:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reminders) { r -> 
                        AssistChip(
                            onClick = { 
                                editingReminder = r
                                showDatePicker = true 
                            }, 
                            label = { Text(DateUtils.formatDate(r.time), fontSize = 11.sp) }, 
                            leadingIcon = { Icon(if (r.isWeekly) Icons.Default.EventRepeat else Icons.Default.Alarm, null, Modifier.size(14.dp)) }, 
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp).clickable { reminders = reminders.filter { it != r } }) }, 
                            shape = RoundedCornerShape(12.dp)
                        ) 
                    }
                }
            }
            TextField(value = titleValue, onValueChange = { titleValue = it }, placeholder = { Text(if (isVietnamese) "Tiêu đề" else "Title", style = MaterialTheme.typography.displayLarge.copy(color = Color.Gray)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), textStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold))
            
            if (noteId != null && noteId != 0L) {
                Text("${if (isVietnamese) "Cập nhật:" else "Updated:"} ${DateUtils.formatDate(currentNote?.updatedAt ?: System.currentTimeMillis())}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, bottom = 16.dp))
            }

            // Phần hiển thị nội dung: Tách text và ảnh
            val contentParts = remember(contentValue.text) {
                val regex = Regex("\\[Hình ảnh: (.*?)\\]")
                val parts = mutableListOf<Pair<String, String?>>()
                var lastIndex = 0
                regex.findAll(contentValue.text).forEach { match ->
                    if (match.range.first > lastIndex) {
                        parts.add(contentValue.text.substring(lastIndex, match.range.first) to null)
                    }
                    parts.add("" to match.groups[1]?.value)
                    lastIndex = match.range.last + 1
                }
                if (lastIndex < contentValue.text.length) {
                    parts.add(contentValue.text.substring(lastIndex) to null)
                }
                parts
            }

            BasicTextField(
                value = contentValue,
                onValueChange = { newValue ->
                    val oldText = contentValue.text
                    val newText = newValue.text
                    if (newText.length == oldText.length + 1 && newValue.selection.start > 0) {
                        val cursor = newValue.selection.start
                        if (newText[cursor - 1] == '\n') {
                            val textBeforeNewline = oldText.substring(0, contentValue.selection.start)
                            val lastLineStart = textBeforeNewline.lastIndexOf('\n') + 1
                            val lastLine = textBeforeNewline.substring(lastLineStart)
                            if (lastLine == "☐ " || lastLine == "☑ ") {
                                val updatedText = oldText.removeRange(lastLineStart, contentValue.selection.start)
                                contentValue = TextFieldValue(updatedText, TextRange(lastLineStart))
                                return@BasicTextField
                            } else if (lastLine.startsWith("☐ ") || lastLine.startsWith("☑ ")) {
                                val prefix = "☐ "
                                val updatedText = StringBuilder(newText).insert(cursor, prefix).toString()
                                contentValue = TextFieldValue(updatedText, TextRange(cursor + prefix.length))
                                return@BasicTextField
                            }
                        }
                    }
                    contentValue = newValue
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, false)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            if (up != null) {
                                val tapOffset = up.position
                                textLayoutResult?.let { layout ->
                                    val charIndex = layout.getOffsetForPosition(tapOffset)
                                    val line = layout.getLineForOffset(charIndex)
                                    val lineStart = layout.getLineStart(line)
                                    val text = contentValue.text
                                    if (lineStart + 2 <= text.length) {
                                        val prefix = text.substring(lineStart, lineStart + 2)
                                        if ((prefix == "☐ " || prefix == "☑ ") && charIndex < lineStart + 2) {
                                            val isChecked = prefix == "☑ "
                                            val newPrefix = if (isChecked) "☐ " else "☑ "
                                            val newContent = text.replaceRange(lineStart, lineStart + 2, newPrefix)
                                            contentValue = TextFieldValue(newContent, contentValue.selection)
                                            up.consume()
                                        }
                                    }
                                }
                            }
                        }
                    },
                onTextLayout = { textLayoutResult = it },
                visualTransformation = noteTransformation,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 32.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (contentValue.text.isEmpty()) Text(if (isVietnamese) "Bắt đầu viết..." else "Start writing...", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                        innerTextField()
                    }
                }
            )

            contentParts.filter { it.second != null }.forEach { part ->
                val imageUri = part.second!!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                    
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .clickable {
                                val tag = "[Hình ảnh: $imageUri]"
                                var newText = contentValue.text
                                if (newText.contains("\n$tag\n")) {
                                    newText = newText.replace("\n$tag\n", "\n")
                                } else if (newText.contains(tag)) {
                                    newText = newText.replace(tag, "")
                                }
                                contentValue = TextFieldValue(newText)
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (isVietnamese) "Xóa ảnh" else "Delete image",
                            tint = Color.White,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            }
        }
    }
    
    // AI Dialog
    if (showAiConfirm) {
        AlertDialog(
            onDismissRequest = { showAiConfirm = false },
            icon = { Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(if (isVietnamese) "Tối ưu bằng AI?" else "Optimize with AI?") },
            text = { Text(if (isVietnamese) "AI sẽ phân tích, viết lại ghi chú của bạn khoa học hơn và tự động trích xuất lịch trình báo thức (nếu có)." else "AI will analyze, rewrite your note more scientifically and automatically extract reminders (if any).") },
            confirmButton = { 
                Button(onClick = { 
                    showAiConfirm = false
                    viewModel.optimizeWithAi(titleValue.text, contentValue.text) { result ->
                        if (result != null) {
                            titleValue = TextFieldValue(result.title)
                            contentValue = TextFieldValue(result.content)
                            if (result.reminderTime != null) {
                                val newReminder = Reminder(reminders.size, result.reminderTime, result.isWeekly)
                                reminders = (reminders + newReminder).distinctBy { it.time }.sortedBy { it.time }
                                Toast.makeText(context, if (isVietnamese) "Đã thêm báo thức tự động!" else "Auto-reminder added!", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, if (isVietnamese) "Lỗi khi gọi AI. Vui lòng kiểm tra API Key!" else "AI Error. Please check API Key!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(if (isVietnamese) "Đồng ý" else "Confirm") }
            },
            dismissButton = { TextButton(onClick = { showAiConfirm = false }) { Text(if (isVietnamese) "Huỷ" else "Cancel") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = false }, title = { Text(if (isVietnamese) "Xóa ghi chú?" else "Delete note?") }, text = { Text(if (isVietnamese) "Ghi chú sẽ được chuyển vào thùng rác!" else "The note will be moved to the trash!") }, confirmButton = { Button(onClick = { currentNote?.let { viewModel.deleteNote(it) }; showDeleteConfirm = false; navController.popBackStack() }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text(if (isVietnamese) "Xác nhận" else "Confirm") } }, dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(if (isVietnamese) "Huỷ" else "Cancel") } }, shape = RoundedCornerShape(24.dp))
    }
}
