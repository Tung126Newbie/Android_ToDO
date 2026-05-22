package com.example.simplenotes.util

import android.util.Log
import com.example.simplenotes.data.remote.ai.AiApiService
import com.example.simplenotes.data.remote.ai.AiChatRequest
import com.example.simplenotes.domain.model.LocalAiResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudAiService @Inject constructor(
    private val aiApiService: AiApiService,
    private val gson: Gson
) {
    private val TAG = "CloudAiService"

    suspend fun processNote(userInput: String, languageCode: String): Result<LocalAiResponse> = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val locale = if (languageCode == "vi") Locale.forLanguageTag("vi") else Locale.ENGLISH
            val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm:ss", locale)
            val formattedTime = sdf.format(Date(currentTime))

            val prompt = """
                Role: Expert Multilingual AI Note Assistant.
                Context: Current time is $formattedTime. Reference day is ${SimpleDateFormat("EEEE", locale).format(Date(currentTime))}.
                Target Language: $languageCode

                Task: Analyze the user's input and generate a structured response.
                
                Fields to generate:
                1. summary: One-sentence summary in $languageCode.
                2. key_points: Array of 2-5 bullet points in $languageCode.
                3. reminder: Catchy notification text in $languageCode.
                4. rewritten_content: Professional Markdown rewrite in $languageCode.
                5. suggested_time: Unix Timestamp in milliseconds (LONG). 
                   - MUST extract intent for reminders, alarms, or specific times.
                   - Handle English: "remind me at 7am", "set alarm for tomorrow", "wake me up at 6".
                   - Handle Vietnamese: "nhắc tôi", "hẹn giờ", "sáng mai", "chiều nay".
                   - If no specific time or intent is found, return null.
                6. ai_question: If suggested_time is present, ask to set the alarm in $languageCode.
                7. is_outdoor_intent: BOOLEAN. True if the user mentions going outside, traveling, working at a specific place, or activities affected by weather.
                8. location_name: STRING. Extract the specific location mentioned (e.g., "Hà Nội", "Đà Lạt", "Công viên"). If no specific location, return null.

                CRITICAL CONSTRAINTS:
                - Return ONLY raw JSON.
                - "suggested_time" MUST BE A NUMBER (e.g. 1713751200000).
                - "is_outdoor_intent" MUST BE A BOOLEAN.
                - Language for all text must be exactly $languageCode.
                - For relative times like "tomorrow", calculate based on the current date provided above.

                User Input:
                $userInput
            """.trimIndent()

            val request = AiChatRequest(question = prompt)
            val response = aiApiService.getAiCompletion(request)

            if (response.isSuccessful) {
                val chatResponse = response.body()
                val answer = chatResponse?.data?.answer
                Log.d(TAG, "Raw AI Answer: $answer")
                
                if (answer != null) {
                    val sanitizedJson = sanitizeJson(answer.trim())
                    try {
                        val jsonObject = gson.fromJson(sanitizedJson, JsonObject::class.java)
                        // ... (giữ nguyên logic xử lý jsonObject)

                        // Check if suggested_time is a string and try to convert to long
                        if (jsonObject.has("suggested_time") && jsonObject.get("suggested_time").isJsonPrimitive) {
                            val primitive = jsonObject.getAsJsonPrimitive("suggested_time")
                            if (primitive.isString) {
                                try {
                                    val longVal = primitive.asString.toLong()
                                    jsonObject.add("suggested_time", JsonPrimitive(longVal))
                                } catch (e: Exception) {
                                    // Not a long string, maybe null or invalid
                                }
                            }
                        }

                        val result = gson.fromJson(jsonObject, LocalAiResponse::class.java)
                        if (result != null) Result.success(result)
                        else Result.failure(Exception("AI returned empty object"))
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON Error: $sanitizedJson", e)
                        Result.failure(Exception("Invalid JSON format from AI"))
                    }
                } else Result.failure(Exception("Empty answer from AI"))
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "API Error 500: $errorBody")
                Result.failure(Exception("API Error: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network/Processing Error", e)
            Result.failure(e)
        }
    }

    private fun sanitizeJson(json: String): String {
        val startIndex = json.indexOf('{')
        val endIndex = json.lastIndexOf('}')
        return if (startIndex != -1 && endIndex != -1) json.substring(startIndex, endIndex + 1) else json
    }
}
