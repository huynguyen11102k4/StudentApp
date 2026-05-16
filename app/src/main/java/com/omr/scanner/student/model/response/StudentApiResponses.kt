package com.omr.scanner.student.model.response

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class StartExamSessionResponse(
    private val exam: StartedExamResponse? = null,
    private val session: StartedSessionResponse? = null,
    val template: StartedTemplateResponse? = null,
    @SerializedName("online_config")
    private val onlineConfig: StartedOnlineConfigResponse? = null,
    val upload: StartedUploadResponse? = null,
    @SerializedName("server_time")
    val serverTime: String? = null,
    @SerializedName("session_id")
    private val legacySessionId: String? = null,
    @SerializedName("attempt_no")
    private val legacyAttemptNo: Int? = null,
    @SerializedName("start_time")
    private val legacyStartTime: String? = null,
    @SerializedName("end_time")
    private val legacyEndTime: String? = null,
    @SerializedName("remaining_seconds")
    private val legacyRemainingSeconds: Int? = null,
    @SerializedName("lock_config")
    private val legacyLockConfig: JsonObject? = null,
    @SerializedName("is_locked_mode")
    private val legacyIsLockedMode: Boolean? = null
) {
    val sessionId: String get() = session?.sessionId ?: legacySessionId.orEmpty()
    val attemptNo: Int get() = session?.attemptNo ?: legacyAttemptNo ?: 0
    val startTime: String get() = session?.startTime ?: legacyStartTime.orEmpty()
    val endTime: String get() = session?.endTime ?: legacyEndTime.orEmpty()
    val remainingSeconds: Int get() = session?.remainingSeconds ?: legacyRemainingSeconds ?: 0
    val lockConfig: JsonObject? get() = onlineConfig?.lockConfig ?: legacyLockConfig
    val isLockedMode: Boolean get() = onlineConfig?.isLockedMode ?: legacyIsLockedMode ?: false
}

data class StartedExamResponse(
    val id: String,
    val name: String,
    val subject: String,
    @SerializedName("duration_minutes")
    val durationMinutes: Int,
    @SerializedName("total_questions")
    val totalQuestions: Int,
    @SerializedName("exam_type")
    val examType: String?,
    @SerializedName("grading_type")
    val gradingType: String?,
    val status: String?
)

data class StartedSessionResponse(
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("attempt_no")
    val attemptNo: Int,
    @SerializedName("start_time")
    val startTime: String,
    @SerializedName("end_time")
    val endTime: String,
    @SerializedName("remaining_seconds")
    val remainingSeconds: Int
)

data class StartedTemplateResponse(
    val id: String,
    @SerializedName("grid_config")
    val gridConfig: JsonObject,
    @SerializedName("base_image_url")
    val baseImageUrl: String? = null,
    @SerializedName("preview_image_url")
    val previewImageUrl: String? = null
)

data class StartedOnlineConfigResponse(
    @SerializedName("is_locked_mode")
    val isLockedMode: Boolean = false,
    @SerializedName("lock_config")
    val lockConfig: JsonObject? = null
)

data class StartedUploadResponse(
    @SerializedName("presign_endpoint")
    val presignEndpoint: String,
    @SerializedName("submit_endpoint")
    val submitEndpoint: String,
    @SerializedName("supported_image_types")
    val supportedImageTypes: List<String>,
    @SerializedName("max_file_size_bytes")
    val maxFileSizeBytes: Long
)

data class PresignSubmissionImageResponse(
    @SerializedName("upload_url")
    val uploadUrl: String,
    @SerializedName("file_url")
    val fileUrl: String,
    @SerializedName("file_path")
    val filePath: String,
    @SerializedName("expires_in")
    val expiresIn: Int
)

data class StudentSubmitResponse(
    @SerializedName("submission_id")
    val submissionId: String,
    val status: String,
    @SerializedName("submitted_at")
    val submittedAt: String,
    @SerializedName("session_status")
    val sessionStatus: String
)

data class LockValidateSessionResponse(
    val valid: Boolean,
    val status: String? = null,
    @SerializedName("remaining_seconds")
    val remainingSeconds: Int? = null,
    @SerializedName("lock_config")
    val lockConfig: JsonObject? = null,
    @SerializedName("is_locked_mode")
    val isLockedMode: Boolean? = null,
    val reason: String? = null
)

data class LockViolationResponse(
    @SerializedName("violation_id")
    val violationId: String,
    @SerializedName("session_status")
    val sessionStatus: String,
    @SerializedName("auto_violated")
    val autoViolated: Boolean
)

data class LockHeartbeatResponse(
    val status: String,
    @SerializedName("remaining_seconds")
    val remainingSeconds: Int
)

data class StudentAppealCreateResponse(
    @SerializedName("appeal_id")
    val appealId: String,
    val status: String,
    @SerializedName("created_at")
    val createdAt: String
)
