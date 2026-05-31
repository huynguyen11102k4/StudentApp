package com.examhub.student.omr.model

data class IdResult(
    val studentId: String,
    val classCode: String,
    val examCode: String,
    val ok: Boolean,
    val error: String = ""
)

data class AnswerResult(
    val questionNumber: Int,
    val answer: String,        // "A", "C", "A,C", "" (no answer)
    val flag: Int = 0          // 0=OK, 1=ambiguous/erasure
)

data class ScoreDetail(
    val questionNumber: Int,
    val studentAnswer: String,
    val isCorrect: Boolean,
    val flag: Int = 0
)

data class ScoreData(
    val totalQuestions: Int,
    val correctCount: Int,
    val totalScore: Double,
    val details: List<ScoreDetail>
)

data class OmrOutput(
    val success: Boolean,
    val errorCode: String = "",
    val errorMessage: String = "",
    val warnings: List<String> = emptyList(),
    val idResult: IdResult = IdResult("", "", "", false),
    val answers: List<AnswerResult> = emptyList(),
    val scored: Boolean = false,
    val scoreResult: ScoreData? = null,
    val laplacianVariance: Float = 0f,
    val meanBrightness: Float = 0f,
    val dewarpedImageBase64: String = "",
    val debugImageBase64: String = ""
)
