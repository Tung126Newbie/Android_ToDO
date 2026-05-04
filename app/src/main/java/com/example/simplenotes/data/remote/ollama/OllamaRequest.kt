package com.example.simplenotes.data.remote.ollama

import com.google.gson.annotations.SerializedName

data class OllamaRequest(
    @SerializedName("model") val model: String = "qwen2.5:7b",
    @SerializedName("prompt") val prompt: String,
    @SerializedName("stream") val stream: Boolean = false,
    @SerializedName("format") val format: String = "json",
    @SerializedName("options") val options: OllamaOptions = OllamaOptions()
)

data class OllamaOptions(
    @SerializedName("temperature") val temperature: Float = 0.2f
)
