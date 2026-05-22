package com.example.simplenotes.data.remote.ai

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AiApiService {
    @POST("v1/api/chat")
    suspend fun getAiCompletion(@Body request: AiChatRequest): Response<AiChatResponse>
}
