package com.examhub.student.model.response.exam

import com.google.gson.JsonElement
import java.io.Serializable

data class ExamTemplateSummaryResponse(
    val id: String,
    val name: String,
    val gridConfig: JsonElement? = null
) : Serializable
