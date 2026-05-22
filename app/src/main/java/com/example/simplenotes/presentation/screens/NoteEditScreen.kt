package com.example.simplenotes.presentation.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.simplenotes.data.repository.AppLanguage
import com.example.simplenotes.domain.model.Reminder
import com.example.simplenotes.presentation.viewmodel.NoteViewModel
import com.example.simplenotes.presentation.viewmodel.SettingsViewModel
import com.example.simplenotes.util.DateUtils
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class NoteHistory(val title: String, val content: String)

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
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentNote by viewModel.currentNote.collectAsState()
    val appLanguage by settingsViewModel.appLanguage.collectAsState()
    val isVietnamese = appLanguage == AppLanguage.VIETNAMESE

    val aiResult by viewModel.aiResult.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val aiError by viewModel.aiError.collectAsState()
    val weatherData by viewModel.weatherData.collectAsState()
    val isWeatherLoading by viewModel.isWeatherLoading.collectAsState()

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
    var showNewNoteConfirm by remember { mutableStateOf(false) }
    var showImageSourceOptions by remember { mutableStateOf(false) }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    var tempImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var fullScreenImageUri by remember { mutableStateOf<String?>(null) }

    // History for Undo/Redo
    val undoStack = remember { mutableStateListOf<NoteHistory>() }
    val redoStack = remember { mutableStateListOf<NoteHistory>() }
    var isHistoryAction by remember { mutableStateOf(false) }

    fun saveToHistory() {
        if (isHistoryAction) {
            isHistoryAction = false
            return
        }
        val currentState = NoteHistory(titleValue.text, contentValue.text)
        if (undoStack.isEmpty() || undoStack.last() != currentState) {
            undoStack.add(currentState)
            if (undoStack.size > 50) undoStack.removeAt(0)
            redoStack.clear()
        }
    }

    fun performUndo() {
        if (undoStack.size > 1) {
            isHistoryAction = true
            val currentState = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(currentState)
            
            val previousState = undoStack.last()
            titleValue = TextFieldValue(previousState.title, TextRange(previousState.title.length))
            contentValue = TextFieldValue(previousState.content, TextRange(previousState.content.length))
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            isHistoryAction = true
            val nextState = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(nextState)
            
            titleValue = TextFieldValue(nextState.title, TextRange(nextState.title.length))
            contentValue = TextFieldValue(nextState.content, TextRange(nextState.content.length))
        }
    }

    LaunchedEffect(titleValue.text, contentValue.text) {
        if (!isInitialLoadDone) return@LaunchedEffect
        delay(1000)
        saveToHistory()
    }

    val noteTransformation = remember {
        VisualTransformation { text ->
            val builder = AnnotatedString.Builder()
            // Hỗ trợ định dạng [Hình ảnh: uri|original_uri] hoặc [Hình ảnh: uri]
            val regex = Regex("\\[Hình ảnh: (.*?)(?:\\|(.*?))?\\]")
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

    fun createPersistentPictureUri(): Uri? {
        return try {
            val file = File(context.filesDir, "captured_image_${System.currentTimeMillis()}.jpg")
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val savedUri = copyUriToInternalStorage(context, it)
            savedUri?.let { uriToSave ->
                val imageTag = "\n[Hình ảnh: $uriToSave]\n"
                contentValue = TextFieldValue(contentValue.text + imageTag, TextRange(contentValue.text.length + imageTag.length))
            } ?: Toast.makeText(context, if (isVietnamese) "Lỗi lưu ảnh" else "Error saving image", Toast.LENGTH_SHORT).show()
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
            val uri = createPersistentPictureUri()
            tempImageUri = uri?.toString()
            uri?.let { cameraLauncher.launch(it) }
        }
    }

    LaunchedEffect(noteId) {
        if (noteId != null && noteId != 0L) {
            viewModel.loadNoteById(noteId)
        } else {
            // Khởi tạo lịch sử cho ghi chú mới
            if (undoStack.isEmpty()) {
                undoStack.add(NoteHistory("", ""))
            }
            isInitialLoadDone = true
        }
    }

    LaunchedEffect(currentNote) {
        if (currentNote != null && !isInitialLoadDone) {
            titleValue = TextFieldValue(currentNote!!.title)
            contentValue = TextFieldValue(currentNote!!.content)
            reminders = currentNote!!.reminders
            isInitialLoadDone = true
            // Lưu trạng thái ban đầu vào lịch sử
            if (undoStack.isEmpty()) {
                undoStack.add(NoteHistory(titleValue.text, contentValue.text))
            }
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

    // Auto Alarm Popup Logic
    var showAlarmConfirmDialog by remember { mutableStateOf(false) }
    var showAdvanceTimePicker by remember { mutableStateOf(false) }
    val advanceOptions = listOf(0, 5, 10, 15, 20, 30, 60)
    LaunchedEffect(aiResult) {
        if (aiResult?.suggestedTime != null && aiResult!!.suggestedTime!! > System.currentTimeMillis()) {
            showAlarmConfirmDialog = true
        }
    }

    if (showAlarmConfirmDialog && aiResult?.suggestedTime != null) {
        AlertDialog(
            onDismissRequest = { showAlarmConfirmDialog = false },
            title = { Text(if (isVietnamese) "Đặt báo thức?" else "Set Alarm?") },
            text = { Text(aiResult?.aiQuestion ?: (if (isVietnamese) "Bạn có muốn đặt báo thức cho mốc thời gian này?" else "Do you want to set an alarm for this time?")) },
            confirmButton = {
                Button(onClick = {
                    showAlarmConfirmDialog = false
                    showAdvanceTimePicker = true
                }) {
                    Text(if (isVietnamese) "Đồng ý" else "Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlarmConfirmDialog = false }) {
                    Text(if (isVietnamese) "Bỏ qua" else "No")
                }
            }
        )
    }

    if (showAdvanceTimePicker && aiResult?.suggestedTime != null) {
        AlertDialog(
            onDismissRequest = { showAdvanceTimePicker = false },
            title = { Text(if (isVietnamese) "Thời gian báo thức" else "Alarm Time") },
            text = {
                Column {
                    Text(
                        text = if (isVietnamese) "Bạn muốn được thông báo trước bao lâu?" else "How long before do you want to be notified?",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    advanceOptions.forEach { minutes ->
                        val label = when (minutes) {
                            0 -> if (isVietnamese) "Đúng giờ" else "At time"
                            else -> "$minutes'"
                        }
                        TextButton(
                            onClick = {
                                val finalTime = aiResult!!.suggestedTime!! - (minutes * 60 * 1000)
                                if (finalTime > System.currentTimeMillis()) {
                                    val newReminder = Reminder(reminders.size, finalTime, false)
                                    reminders = (reminders + newReminder).sortedBy { it.time }
                                    Toast.makeText(context, if (isVietnamese) "Đã thêm báo thức!" else "Alarm added!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, if (isVietnamese) "Thời gian không hợp lệ!" else "Invalid time!", Toast.LENGTH_SHORT).show()
                                }
                                showAdvanceTimePicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAdvanceTimePicker = false }) {
                    Text(if (isVietnamese) "Hủy" else "Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = { saveNote() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(
                        onClick = { performUndo() },
                        enabled = undoStack.size > 1
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            "Undo", 
                            tint = if (undoStack.size > 1) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                    IconButton(
                        onClick = { performRedo() },
                        enabled = redoStack.isNotEmpty()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            "Redo", 
                            tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                    IconButton(onClick = { 
                        keyboardController?.hide()
                        viewModel.processNoteWithAi(contentValue.text)
                    }) { 
                        if (isAiLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.AutoAwesome, "AI Assistant", tint = MaterialTheme.colorScheme.secondary) 
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
                IconButton(onClick = { 
                    if (hasChanges) {
                        showNewNoteConfirm = true
                    } else {
                        viewModel.clearCurrentNote()
                        navController.navigate("edit")
                    }
                }) { Icon(Icons.Default.Add, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary) }
                if (noteId != null && noteId != 0L) IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.DeleteOutline, null, Modifier.size(32.dp)) }
            }
        }
    ) { pv ->
        Column(Modifier.fillMaxSize().padding(pv).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)) {
            
            // AI Response Display
            if (aiResult != null || aiError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isVietnamese) "AI Assistant" else "AI Assistant", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { viewModel.clearAiResult() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                        
                        if (aiError != null) {
                            Text(aiError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }

                        aiResult?.let { result ->
                            Spacer(Modifier.height(8.dp))
                            Text(if (isVietnamese) "Tóm tắt:" else "Summary:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(result.summary, style = MaterialTheme.typography.bodyMedium)
                            
                            Spacer(Modifier.height(8.dp))
                            Text(if (isVietnamese) "Ý chính:" else "Key Points:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            result.keyPoints.forEach { point ->
                                Text("• $point", style = MaterialTheme.typography.bodyMedium)
                            }
                            
                            if (!result.reminder.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(if (isVietnamese) "Gợi ý nhắc nhở:" else "Reminder Suggestion:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(result.reminder, style = MaterialTheme.typography.bodyMedium)
                            }

                            // Weather Display
                            if (isWeatherLoading) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            } else if (weatherData != null) {
                                Spacer(Modifier.height(12.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (weatherData!!.locationName != null) 
                                                    (if (isVietnamese) "Thời tiết tại ${weatherData!!.locationName}:" else "Weather at ${weatherData!!.locationName}:")
                                                    else (if (isVietnamese) "Thời tiết hiện tại:" else "Current weather:"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${weatherData!!.temperature}°C - ${weatherData!!.getWeatherDescription(isVietnamese)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Icon(
                                            imageVector = if (weatherData!!.isDay) Icons.Default.WbSunny else Icons.Default.NightsStay,
                                            contentDescription = null,
                                            tint = if (weatherData!!.isDay) Color(0xFFFFB300) else Color(0xFF5C6BC0)
                                        )
                                    }
                                }
                            } else if (result.isOutdoorIntent == true) {
                                // Buttons to fetch weather if not already fetched
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!result.locationName.isNullOrBlank()) {
                                        AssistChip(
                                            onClick = { viewModel.fetchWeatherByLocationName(result.locationName) },
                                            label = { Text(if (isVietnamese) "Xem thời tiết ${result.locationName}" else "Check weather for ${result.locationName}", fontSize = 10.sp) },
                                            leadingIcon = { Icon(Icons.Default.Cloud, null, modifier = Modifier.size(14.dp)) }
                                        )
                                    }
                                    AssistChip(
                                        onClick = { viewModel.fetchWeatherForCurrentLocation() },
                                        label = { Text(if (isVietnamese) "Vị trí hiện tại" else "Current Location", fontSize = 10.sp) },
                                        leadingIcon = { Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                            }

                            // AI Action Buttons
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Nút Áp dụng thay đổi
                                    val applyContent = result.rewrittenContent ?: result.reminder
                                    if (!applyContent.isNullOrBlank()) {
                                        Button(
                                            onClick = { contentValue = TextFieldValue(applyContent) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Text(if (isVietnamese) "Áp dụng thay đổi" else "Apply Changes", fontSize = 11.sp, maxLines = 1)
                                        }
                                    }

                                    // Nút Hoàn tác
                                    OutlinedButton(
                                        onClick = { performUndo() },
                                        modifier = Modifier.weight(1f),
                                        enabled = undoStack.size > 1,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (isVietnamese) "Hoàn tác" else "Undo", fontSize = 11.sp, maxLines = 1)
                                    }
                                }

                                // NÚT: Đặt báo thức (chỉ hiện nếu có thời gian gợi ý)
                                if (result.suggestedTime != null && result.suggestedTime > System.currentTimeMillis()) {
                                    Button(
                                        onClick = {
                                            showAdvanceTimePicker = true
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.AccessTime, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (isVietnamese) "Đặt báo thức" else "Set Alarm", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                val regex = Regex("\\[Hình ảnh: (.*?)(?:\\|(.*?))?\\]")
                val parts = mutableListOf<Triple<String, String?, String?>>()
                var lastIndex = 0
                regex.findAll(contentValue.text).forEach { match ->
                    if (match.range.first > lastIndex) {
                        parts.add(Triple(contentValue.text.substring(lastIndex, match.range.first), null, null))
                    }
                    val currentUri = match.groups[1]?.value
                    val originalUri = match.groups[2]?.value ?: currentUri
                    parts.add(Triple("", currentUri, originalUri))
                    lastIndex = match.range.last + 1
                }
                if (lastIndex < contentValue.text.length) {
                    parts.add(Triple(contentValue.text.substring(lastIndex), null, null))
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
                val currentUri = part.second!!
                val originalUri = part.third!!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    AsyncImage(
                        model = android.net.Uri.parse(currentUri),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { fullScreenImageUri = if (originalUri != currentUri) "$currentUri|$originalUri" else currentUri },
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
                                val tagWithOriginal = "[Hình ảnh: $currentUri|$originalUri]"
                                val tagSimple = "[Hình ảnh: $currentUri]"
                                var newText = contentValue.text
                                if (newText.contains("\n$tagWithOriginal\n")) {
                                    newText = newText.replace("\n$tagWithOriginal\n", "\n")
                                } else if (newText.contains(tagWithOriginal)) {
                                    newText = newText.replace(tagWithOriginal, "")
                                } else if (newText.contains("\n$tagSimple\n")) {
                                    newText = newText.replace("\n$tagSimple\n", "\n")
                                } else if (newText.contains(tagSimple)) {
                                    newText = newText.replace(tagSimple, "")
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

    if (fullScreenImageUri != null) {
        val parts = fullScreenImageUri!!.split("|")
        val currentUri = parts[0]
        val originalUri = if (parts.size > 1) parts[1] else currentUri

        FullScreenImageDialog(
            imageUri = currentUri,
            actualOriginalUri = originalUri,
            onDismiss = { fullScreenImageUri = null },
            onImageUpdate = { oldUri, newUri, isReset ->
                val escapedOld = Regex.escape(oldUri)
                val escapedOrig = Regex.escape(originalUri)
                val oldTagRegex = Regex("\\[Hình ảnh: $escapedOld(?:\\|$escapedOrig)?\\]")
                
                val newTag = if (isReset) "[Hình ảnh: $newUri]" else "[Hình ảnh: $newUri|$originalUri]"
                
                val newText = contentValue.text.replace(oldTagRegex, newTag)
                contentValue = contentValue.copy(text = newText)
                fullScreenImageUri = if (isReset) newUri else "$newUri|$originalUri"
            },
            isVietnamese = isVietnamese
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = false }, title = { Text(if (isVietnamese) "Xóa ghi chú?" else "Delete note?") }, text = { Text(if (isVietnamese) "Ghi chú sẽ được chuyển vào thùng rác!" else "The note will be moved to the trash!") }, confirmButton = { Button(onClick = { currentNote?.let { viewModel.deleteNote(it) }; showDeleteConfirm = false; navController.popBackStack() }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text(if (isVietnamese) "Xác nhận" else "Confirm") } }, dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(if (isVietnamese) "Huỷ" else "Cancel") } }, shape = RoundedCornerShape(24.dp))
    }

    if (showNewNoteConfirm) {
        AlertDialog(
            onDismissRequest = { showNewNoteConfirm = false },
            title = { Text(if (isVietnamese) "Lưu thay đổi?" else "Save changes?") },
            text = { Text(if (isVietnamese) "Bạn có muốn lưu ghi chú này trước khi tạo mới không?" else "Do you want to save this note before creating a new one?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.insertOrUpdateNote(titleValue.text, contentValue.text, reminders) {
                        showNewNoteConfirm = false
                        viewModel.clearCurrentNote()
                        navController.navigate("edit")
                    }
                }) {
                    Text(if (isVietnamese) "Lưu" else "Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showNewNoteConfirm = false
                        viewModel.clearCurrentNote()
                        navController.navigate("edit")
                    }) {
                        Text(if (isVietnamese) "Không lưu" else "Discard")
                    }
                    TextButton(onClick = { showNewNoteConfirm = false }) {
                        Text(if (isVietnamese) "Hủy" else "Cancel")
                    }
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun FullScreenImageDialog(
    imageUri: String,
    actualOriginalUri: String,
    onDismiss: () -> Unit,
    onImageUpdate: (String, String, Boolean) -> Unit,
    isVietnamese: Boolean
) {
    val context = LocalContext.current
    
    // Reset states when imageUri changes (after Save/Crop)
    var scale by remember(imageUri) { mutableFloatStateOf(1f) }
    var offset by remember(imageUri) { mutableStateOf(Offset.Zero) }
    var rotation by remember(imageUri) { mutableFloatStateOf(0f) }
    var isFlipped by remember(imageUri) { mutableStateOf(false) }
    
    // Zoom/Pan/Rotate state cho UI hiển thị
    val state = rememberTransformableState { zoomChange: Float, offsetChange: Offset, rotationChange: Float ->
        scale *= zoomChange
        offset += offsetChange
        rotation += rotationChange
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = android.net.Uri.parse(imageUri),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale * (if (isFlipped) -1f else 1f),
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                        rotationZ = rotation
                    )
                    .transformable(state = state),
                contentScale = ContentScale.Fit
            )

            // Điều khiển phía trên
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
                
                Row {
                    IconButton(onClick = {
                        rotation = (rotation + 90f) % 360f
                    }) {
                        Icon(Icons.Default.RotateRight, null, tint = Color.White)
                    }
                    
                    IconButton(onClick = {
                        isFlipped = !isFlipped
                    }) {
                        Icon(Icons.Default.Flip, null, tint = Color.White)
                    }
                }
            }

            // Toolbar chỉnh sửa phía dưới
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Nút Crop 1:1 (Ví dụ)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                processImageEffect(context, imageUri, rotation, isFlipped, true) { newUri ->
                                    onImageUpdate(imageUri, newUri, false)
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Crop, null, tint = Color.White)
                        Text(if (isVietnamese) "Cắt ảnh" else "Crop", color = Color.White, fontSize = 10.sp)
                    }
                    
                    // Nút Reset
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                scale = 1f
                                offset = Offset.Zero
                                rotation = 0f
                                isFlipped = false
                                // Khôi phục lại ảnh gốc thực sự (lấy từ dữ liệu bền vững)
                                if (imageUri != actualOriginalUri) {
                                    onImageUpdate(imageUri, actualOriginalUri, true)
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = Color.White)
                        Text(if (isVietnamese) "Đặt lại" else "Reset", color = Color.White, fontSize = 10.sp)
                    }

                    // Nút Lưu thay đổi vật lý
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                processImageEffect(context, imageUri, rotation, isFlipped, false) { newUri ->
                                    onImageUpdate(imageUri, newUri, false)
                                    Toast.makeText(context, if (isVietnamese) "Đã lưu thay đổi" else "Changes saved", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Save, null, tint = Color.White)
                        Text(if (isVietnamese) "Lưu" else "Save", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// Hàm xử lý ảnh vật lý (Xoay/Cắt) và lưu vào file mới
fun processImageEffect(
    context: Context,
    imageUri: String,
    rotation: Float,
    isFlipped: Boolean,
    isCropSquare: Boolean,
    onResult: (String) -> Unit
) {
    try {
        val inputStream = context.contentResolver.openInputStream(Uri.parse(imageUri))
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        
        val matrix = Matrix()
        matrix.postRotate(rotation)
        if (isFlipped) {
            matrix.postScale(-1f, 1f)
        }
        
        var processedBitmap = Bitmap.createBitmap(
            originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
        )

        if (isCropSquare) {
            val size = minOf(processedBitmap.width, processedBitmap.height)
            val x = (processedBitmap.width - size) / 2
            val y = (processedBitmap.height - size) / 2
            processedBitmap = Bitmap.createBitmap(processedBitmap, x, y, size, size)
        }

        val file = File(context.filesDir, "edited_image_${System.currentTimeMillis()}.jpg")
        val out = FileOutputStream(file)
        processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.flush()
        out.close()
        
        onResult(Uri.fromFile(file).toString())
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun copyUriToInternalStorage(context: Context, uri: Uri): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "saved_image_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
