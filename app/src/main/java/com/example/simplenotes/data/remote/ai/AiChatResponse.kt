package com.example.simplenotes.data.remote.ai

data class AiChatResponse(
    val status: String,
    val data: AiData
)

data class AiData(
    val answer: String
)
