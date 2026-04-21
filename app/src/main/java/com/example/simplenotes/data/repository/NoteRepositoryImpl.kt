package com.example.simplenotes.data.repository

import com.example.simplenotes.data.database.NoteDao
import com.example.simplenotes.data.database.NoteEntity
import com.example.simplenotes.domain.model.Note
import com.example.simplenotes.domain.model.Reminder
import com.example.simplenotes.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao
) : NoteRepository {
    override fun getAllNotes(): Flow<List<Note>> {
        return noteDao.getAllNotes().map { entities ->
            entities.map { it.toNote() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getNoteById(id: Long): Note? {
        return noteDao.getNoteById(id)?.toNote()
    }

    override suspend fun insertNote(note: Note): Long {
        return noteDao.insertNote(note.toEntity())
    }

    override suspend fun updateNote(note: Note) {
        noteDao.updateNote(note.toEntity())
    }

    override suspend fun updateNotes(notes: List<Note>) {
        noteDao.updateNotes(notes.map { it.toEntity() })
    }

    override suspend fun deleteNote(note: Note) {
        noteDao.deleteNote(note.toEntity())
    }
}

// Extension functions for mapping
fun NoteEntity.toNote(): Note {
    val reminders = if (remindersJson.isBlank()) {
        emptyList<Reminder>()
    } else {
        remindersJson.split(",").mapIndexedNotNull { index, s ->
            val parts = s.split("|")
            if (parts.size == 2) {
                Reminder(
                    id = index,
                    time = parts[0].toLongOrNull() ?: 0L,
                    isWeekly = parts[1].toBoolean()
                )
            } else null
        }
    }
    
    return Note(
        id = id,
        title = title,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        deletedAt = deletedAt,
        reminders = reminders,
        position = position
    )
}

fun Note.toEntity(): NoteEntity {
    val remindersJson = reminders.joinToString(",") { "${it.time}|${it.isWeekly}" }
    return NoteEntity(
        id = id,
        title = title,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        deletedAt = deletedAt,
        remindersJson = remindersJson,
        position = position
    )
}
