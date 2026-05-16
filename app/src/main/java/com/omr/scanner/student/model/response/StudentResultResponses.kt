package com.omr.scanner.student.model.response

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class StudentResultSummaryResponse(
    val id: String,
    @SerializedName(value = "total_score", alternate = ["totalScore"])
    val totalScore: Double? = null,
    @SerializedName(value = "processed_image_url", alternate = ["processedImageUrl"])
    val processedImageUrl: String? = null,
    val source: String? = null,
    @SerializedName(value = "graded_at", alternate = ["gradedAt"])
    val gradedAt: String? = null,
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: String? = null,
    val exam: StudentResultExamResponse? = null
) : Serializable

data class StudentResultDetailResponse(
    val id: String,
    @SerializedName(value = "total_score", alternate = ["totalScore"])
    val totalScore: Double? = null,
    @SerializedName(value = "processed_image_url", alternate = ["processedImageUrl"])
    val processedImageUrl: String? = null,
    @SerializedName(value = "raw_image_url", alternate = ["rawImageUrl"])
    val rawImageUrl: String? = null,
    @SerializedName(value = "dewarped_image_url", alternate = ["dewarpedImageUrl"])
    val dewarpedImageUrl: String? = null,
    val source: String? = null,
    @SerializedName(value = "graded_at", alternate = ["gradedAt"])
    val gradedAt: String? = null,
    val exam: StudentResultExamResponse? = null,
    @SerializedName(value = "answer_details", alternate = ["answerDetails"])
    val answerDetails: List<StudentResultAnswerResponse> = emptyList()
) : Serializable

data class StudentResultExamResponse(
    val id: String? = null,
    val name: String? = null,
    val subject: String? = null,
    @SerializedName(value = "total_questions", alternate = ["totalQuestions"])
    val totalQuestions: Int? = null,
    val duration: Int? = null
) : Serializable

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
) : Serializable
