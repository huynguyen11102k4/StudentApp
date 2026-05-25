package com.examhub.student.model.response.result

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement
import java.io.Serializable

data class StudentResultAnswerResponse(
    @SerializedName(value = "question_number", alternate = ["questionNumber"])
    val questionNumber: Int,
    @SerializedName(value = "student_answer", alternate = ["studentAnswer"])
    val studentAnswer: String? = null,
    @SerializedName(value = "correct_answer", alternate = ["correctAnswer"])
    val correctAnswer: JsonElement? = null,
    @SerializedName(value = "is_correct", alternate = ["isCorrect"])
    val isCorrect: Boolean? = null,
    val score: Double? = null,
    @SerializedName(value = "is_flagged", alternate = ["isFlagged"])
    val isFlagged: Boolean? = null
) {
    fun correctAnswerText(): String? {
        val value = correctAnswer ?: return null
        return when {
            value.isJsonArray -> value.asJsonArray
                .mapNotNull { element ->
                    element.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
                }
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            value.isJsonPrimitive -> value.asString.takeIf { it.isNotBlank() }
            else -> null
        }
    }
}
