package com.example.simplenotes.domain.model

import com.google.gson.annotations.SerializedName

data class LocalAiResponse(
    @SerializedName("summary") val summary: String,
    @SerializedName("key_points") val keyPoints: List<String>,
    @SerializedName("reminder") val reminder: String,
    @SerializedName("rewritten_content") val rewrittenContent: String? = null,
    @SerializedName("suggested_time") val suggestedTime: Long? = null, // Epoch millis
    @SerializedName("ai_question") val aiQuestion: String? = null // Question for user
)
