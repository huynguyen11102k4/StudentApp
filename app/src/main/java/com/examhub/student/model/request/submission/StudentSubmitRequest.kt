package com.examhub.student.model.request.submission

import com.google.gson.annotations.SerializedName

data class StudentSubmitRequest(
    @SerializedName("client_submission_id")
    val clientSubmissionId: String,
    @SerializedName("offline_permit")
    val offlinePermit: String? = null,
    @SerializedName("payload_sha256")
    val payloadSha256: String? = null,
    @SerializedName("raw_image_url")
    val rawImageUrl: String? = null,
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
