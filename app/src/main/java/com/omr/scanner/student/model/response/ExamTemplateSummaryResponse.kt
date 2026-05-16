package com.omr.scanner.student.model.response

import com.google.gson.JsonObject
import java.io.Serializable

data class ExamTemplateSummaryResponse(
    val id: String,
    val name: String,
    val gridConfig: JsonObject?
) : Serializable
