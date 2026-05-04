package com.example.simplenotes.data.remote.ollama

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaApi {
    @POST("api/generate")
    suspend fun generate(@Body request: OllamaRequest): Response<OllamaResponse>
}
