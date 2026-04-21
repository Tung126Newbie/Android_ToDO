package com.example.simplenotes.domain.model

data class Reminder(
    val id: Int,
    val time: Long,
    val isWeekly: Boolean = false
)

data class Note(
    val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val reminders: List<Reminder> = emptyList(),
    val position: Int = 0
)
