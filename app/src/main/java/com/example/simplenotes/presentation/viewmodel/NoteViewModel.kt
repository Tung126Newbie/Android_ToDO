package com.example.simplenotes.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplenotes.data.repository.AppLanguage
import com.example.simplenotes.data.repository.NoteRepositoryImpl
import com.example.simplenotes.data.repository.UserPreferencesRepository
import com.example.simplenotes.domain.model.Note
import com.example.simplenotes.domain.model.Reminder
import com.example.simplenotes.domain.model.LocalAiResponse
import com.example.simplenotes.domain.repository.NoteRepository
import com.example.simplenotes.presentation.util.ReminderScheduler
import com.example.simplenotes.util.LocalAiService
import com.example.simplenotes.util.StringUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortType {
    CUSTOM,
    CREATED_NEWEST,
    A_Z,
    REMINDER_SOON
}

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val reminderScheduler: ReminderScheduler,
    private val localAiService: LocalAiService,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _sortType = MutableStateFlow(SortType.CUSTOM)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    
    val notes: StateFlow<List<Note>> = combine(_notes, _sortType) { notes, sortType ->
        val activeNotes = notes.filter { !it.isDeleted }
        when (sortType) {
            SortType.CUSTOM -> activeNotes.sortedBy { it.position }
            SortType.CREATED_NEWEST -> activeNotes.sortedByDescending { it.createdAt }
            SortType.A_Z -> activeNotes.sortedBy { it.title.lowercase() }
            SortType.REMINDER_SOON -> {
                activeNotes.sortedWith(compareBy({ it.reminders.isEmpty() }, { it.reminders.minByOrNull { r -> r.time }?.time }))
            }
        }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredNotes: StateFlow<List<Note>> = combine(notes, _searchQuery) { notes, query ->
        if (query.isBlank()) {
            notes
        } else {
            val normalizedQuery = StringUtils.removeAccents(query)
            notes.filter {
                StringUtils.removeAccents(it.title).contains(normalizedQuery, ignoreCase = true) ||
                StringUtils.removeAccents(it.content).contains(normalizedQuery, ignoreCase = true)
            }
        }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedNotes: StateFlow<List<Note>> = _notes.map { list ->
        list.filter { it.isDeleted }.sortedByDescending { it.deletedAt }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentNote = MutableStateFlow<Note?>(null)
    val currentNote: StateFlow<Note?> = _currentNote.asStateFlow()

    // AI States
    private val _aiResult = MutableStateFlow<LocalAiResponse?>(null)
    val aiResult: StateFlow<LocalAiResponse?> = _aiResult.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    init {
        loadNotes()
    }

    fun loadNotes() {
        viewModelScope.launch {
            repository.getAllNotes().collect { noteList ->
                _notes.value = noteList
            }
        }
    }

    fun setSortType(type: SortType) {
        _sortType.value = type
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun swapNotes(fromId: Long, toId: Long) {
        val currentList = _notes.value.toMutableList()
        val fromIndex = currentList.indexOfFirst { it.id == fromId }
        val toIndex = currentList.indexOfFirst { it.id == toId }
        
        if (fromIndex != -1 && toIndex != -1) {
            val fromNote = currentList[fromIndex]
            val toNote = currentList[toIndex]
            
            currentList[fromIndex] = fromNote.copy(position = toNote.position)
            currentList[toIndex] = toNote.copy(position = fromNote.position)
            
            _notes.value = currentList
            _sortType.value = SortType.CUSTOM
            
            viewModelScope.launch {
                repository.updateNotes(listOf(currentList[fromIndex], currentList[toIndex]))
            }
        }
    }

    fun loadNoteById(id: Long) {
        viewModelScope.launch {
            val note = repository.getNoteById(id)
            _currentNote.value = note
        }
    }

    fun setCurrentNote(note: Note?) {
        _currentNote.value = note
    }

    fun insertOrUpdateNote(
        title: String,
        content: String,
        reminders: List<Reminder>,
        onResult: (isSaved: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existingNote = _currentNote.value
            
            if (existingNote != null) {
                val isChanged = existingNote.title != title || 
                                existingNote.content != content || 
                                existingNote.reminders.map { it.time to it.isWeekly } != reminders.map { it.time to it.isWeekly }
                
                if (isChanged) {
                    val updatedNote = existingNote.copy(
                        title = title,
                        content = content,
                        updatedAt = now,
                        reminders = reminders
                    )
                    repository.updateNote(updatedNote)
                    updateReminders(updatedNote)
                    _currentNote.value = updatedNote
                    onResult(true)
                } else {
                    onResult(false)
                }
            } else {
                if (title.isNotBlank() || content.isNotBlank() || reminders.isNotEmpty()) {
                    val maxPosition = _notes.value.maxOfOrNull { it.position } ?: -1
                    val newNote = Note(
                        title = title,
                        content = content,
                        createdAt = now,
                        updatedAt = now,
                        reminders = reminders,
                        position = maxPosition + 1
                    )
                    val id = repository.insertNote(newNote)
                    val savedNote = newNote.copy(id = id)
                    updateReminders(savedNote)
                    _currentNote.value = savedNote
                    onResult(true)
                } else {
                    onResult(false)
                }
            }
        }
    }

    fun moveNoteToTrash(note: Note) {
        viewModelScope.launch {
            val updatedNote = note.copy(
                isDeleted = true,
                deletedAt = System.currentTimeMillis()
            )
            repository.updateNote(updatedNote)
            cancelAllReminders(note.id)
        }
    }

    fun restoreNote(note: Note) {
        viewModelScope.launch {
            val updatedNote = note.copy(
                isDeleted = false,
                deletedAt = null
            )
            repository.updateNote(updatedNote)
            updateReminders(updatedNote)
        }
    }

    fun permanentlyDeleteNote(note: Note) {
        viewModelScope.launch {
            cancelAllReminders(note.id)
            repository.deleteNote(note)
        }
    }

    fun clearTrash() {
        viewModelScope.launch {
            _notes.value.filter { it.isDeleted }.forEach { note ->
                cancelAllReminders(note.id)
                repository.deleteNote(note)
            }
        }
    }

    private fun updateReminders(note: Note) {
        for (i in 0..50) {
            reminderScheduler.cancelReminder(note.id.toInt() * 100 + i)
        }
        
        note.reminders.forEachIndexed { index, reminder ->
            if (reminder.time > System.currentTimeMillis()) {
                val uniqueId = note.id.toInt() * 100 + index
                reminderScheduler.scheduleReminder(
                    uniqueId = uniqueId,
                    title = note.title.ifBlank { "Ghi chú không có tiêu đề" },
                    reminderTime = reminder.time,
                    isWeekly = reminder.isWeekly,
                    noteId = note.id
                )
            }
        }
    }

    private fun cancelAllReminders(noteId: Long) {
        for (i in 0..50) {
            reminderScheduler.cancelReminder(noteId.toInt() * 100 + i)
        }
    }

    fun deleteNote(note: Note) {
        moveNoteToTrash(note)
    }

    fun clearCurrentNote() {
        _currentNote.value = null
        _aiResult.value = null
        _aiError.value = null
    }

    fun processNoteWithAi(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiError.value = null
            _aiResult.value = null
            
            val language = userPreferencesRepository.appLanguageFlow.first()
            val languageCode = if (language == AppLanguage.VIETNAMESE) "vi" else "en"
            
            localAiService.processNote(content, languageCode)
                .onSuccess {
                    _aiResult.value = it
                }
                .onFailure {
                    _aiError.value = it.message ?: "Unknown error occurred"
                }
            
            _isAiLoading.value = false
        }
    }

    fun clearAiResult() {
        _aiResult.value = null
        _aiError.value = null
    }
}
