package com.example.simplenotes.util

import com.example.simplenotes.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiService @Inject constructor() {
    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val TAG = "GeminiService"
    
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 1024
            responseMimeType = "application/json"
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.ONLY_HIGH),
        )
    )

    data class AiResponse(
        val title: String,
        val content: String,
        val reminderTime: Long?,
        val isWeekly: Boolean
    )

    suspend fun optimizeNote(title: String, content: String): AiResponse? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) {
            Log.e(TAG, "Lỗi: API Key chưa được thiết lập!")
            return@withContext null
        }
        
        try {
            Log.d(TAG, "Đang gửi yêu cầu tới Gemini với Tiêu đề: $title")
            
            val prompt = """
                Bạn là một chuyên gia quản lý ghi chú. Hãy tối ưu ghi chú sau:
                Tiêu đề: $title
                Nội dung: $content
                
                Nhiệm vụ:
                1. Viết lại tiêu đề và nội dung rõ ràng, khoa học.
                2. Trích xuất thời gian báo thức nếu có (dưới dạng timestamp milis).
                3. Trả về JSON duy nhất:
                {
                  "title": "tiêu đề",
                  "content": "nội dung",
                  "reminderTime": 1730000000000,
                  "isWeekly": false
                }
                Lưu ý: reminderTime để null nếu không có thời gian cụ thể. Hiện tại: ${System.currentTimeMillis()}.
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val jsonText = response.text?.trim() ?: run {
                Log.e(TAG, "AI trả về response trống. Có thể bị chặn bởi Safety Filters.")
                return@withContext null
            }
            
            Log.d(TAG, "Raw Response: $jsonText")

            val cleanedJson = if (jsonText.startsWith("```")) {
                jsonText.replace(Regex("```json|```"), "").trim()
            } else {
                jsonText
            }

            // Kiểm tra JSON không rỗng trước khi parse
            if (cleanedJson.isBlank()) {
                Log.e(TAG, "JSON rỗng sau khi xử lý response")
                return@withContext null
            }

            val json = try {
                JSONObject(cleanedJson)
            } catch (e: Exception) {
                Log.e(TAG, "Không thể parse JSON từ response: $cleanedJson", e)
                return@withContext null
            }
            
            AiResponse(
                title = json.optString("title", title),
                content = json.optString("content", content),
                reminderTime = if (json.isNull("reminderTime")) null else json.getLong("reminderTime"),
                isWeekly = json.optBoolean("isWeekly", false)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi Gemini API: ${e.message}", e)
            null
        }
    }
}
