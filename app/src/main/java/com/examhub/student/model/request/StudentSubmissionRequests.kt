package com.examhub.student.model.request

import com.google.gson.annotations.SerializedName

data class PresignSubmissionImageRequest(
    @SerializedName("file_size")
    val fileSize: Long,
    @SerializedName("file_type")
    val fileType: String,
    @SerializedName("image_kind")
    val imageKind: String? = null
)

data class StudentSubmitRequest(
    @SerializedName("raw_image_url")
    val rawImageUrl: String,
    @SerializedName("dewarped_image_url")
    val dewarpedImageUrl: String? = null,
    @SerializedName("processed_image_url")
    val processedImageUrl: String? = null,
    @SerializedName("student_id")
    val scannedStudentId: String? = null,
    @SerializedName("class_code")
    val scannedClassCode: String? = null,
    @SerializedName("exam_code")
    val scannedExamCode: String? = null,
    @SerializedName("id_result")
    val idResult: IdZoneResultRequest? = null,
    @SerializedName("student_answers")
    val studentAnswers: List<StudentAnswerRequest>,
    @SerializedName("captured_at")
    val capturedAt: String,
    @SerializedName("image_quality_score")
    val imageQualityScore: Int? = null,
    @SerializedName("quality_feedback")
    val qualityFeedback: Map<String, String>? = null
)

data class StudentAnswerRequest(
    @SerializedName("question_number")
    val questionNumber: Int,
    val answer: String?
)

data class IdZoneResultRequest(
    @SerializedName("student_id")
    val studentId: String?,
    @SerializedName("class_code")
    val classCode: String?,
    @SerializedName("exam_code")
    val examCode: String?,
    @SerializedName("id_ok")
    val idOk: Boolean,
    @SerializedName("id_error")
    val idError: String? = null
)
