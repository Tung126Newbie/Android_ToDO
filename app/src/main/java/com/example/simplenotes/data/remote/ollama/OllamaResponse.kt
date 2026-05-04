package com.example.simplenotes.data.remote.ollama

import com.google.gson.annotations.SerializedName

data class OllamaResponse(
    @SerializedName("model") val model: String,
    @SerializedName("response") val response: String,
    @SerializedName("done") val done: Boolean
)
