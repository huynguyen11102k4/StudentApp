package com.examhub.student.omr

import com.examhub.student.data.model.Answer

data class OmrProcessingResult(
    val examId: String,
    val sessionId: String,
    val studentId: String,
    val classCode: String?,
    val examCode: String?,
    val studentIdEnabled: Boolean,
    val classCodeEnabled: Boolean,
    val examCodeEnabled: Boolean,
    val idOk: Boolean,
    val idError: String?,
    val studentName: String?,
    val answers: List<Answer>,
    val totalScore: Double?,
    val rawImageBase64: String,
    val dewarpedImageBase64: String,
    val debugImageBase64: String,
    val laplacianVariance: Float,
    val meanBrightness: Float,
    val warnings: List<String>
)
