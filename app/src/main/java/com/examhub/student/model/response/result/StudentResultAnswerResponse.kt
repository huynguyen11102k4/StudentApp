package com.examhub.student.model.response.result

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class StudentResultAnswerResponse(
    @SerializedName(value = "question_number", alternate = ["questionNumber"])
    val questionNumber: Int,
    @SerializedName(value = "student_answer", alternate = ["studentAnswer"])
    val studentAnswer: String? = null,
    @SerializedName(value = "correct_answer", alternate = ["correctAnswer"])
    val correctAnswer: String? = null,
    @SerializedName(value = "is_correct", alternate = ["isCorrect"])
    val isCorrect: Boolean? = null,
    val score: Double? = null,
    @SerializedName(value = "is_flagged", alternate = ["isFlagged"])
    val isFlagged: Boolean? = null
)
