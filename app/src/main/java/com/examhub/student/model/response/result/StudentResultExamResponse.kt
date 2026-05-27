package com.examhub.student.model.response.result

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class StudentResultExamResponse(
    val id: String? = null,
    val name: String? = null,
    val subject: String? = null,
    val status: String? = null,
    @SerializedName(value = "total_questions", alternate = ["totalQuestions"])
    val totalQuestions: Int? = null,
    val duration: Int? = null
)
