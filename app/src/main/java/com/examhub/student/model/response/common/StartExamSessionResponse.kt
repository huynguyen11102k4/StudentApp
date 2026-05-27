package com.examhub.student.model.response.common

import com.google.gson.JsonElement
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
    private val legacyLockConfig: JsonElement? = null,
    @SerializedName("is_locked_mode")
    private val legacyIsLockedMode: Boolean? = null
) {
    val sessionId: String get() = session?.sessionId ?: legacySessionId.orEmpty()
    val studentCodeType: String? get() = exam?.studentCodeType ?: exam?.studentIdentifier?.mode
    val studentIdentifierCode: String? get() = exam?.studentIdentifier?.code
    val studentIdentifierClassCode: String? get() = exam?.studentIdentifier?.classCode
    val examClassCode: String? get() = exam?.classInfo?.classCode
    val attemptNo: Int get() = session?.attemptNo ?: legacyAttemptNo ?: 0
    val startTime: String get() = session?.startTime ?: legacyStartTime.orEmpty()
    val endTime: String get() = session?.endTime ?: legacyEndTime.orEmpty()
    val remainingSeconds: Int get() = session?.remainingSeconds ?: legacyRemainingSeconds ?: 0
    val lockConfig: JsonElement? get() = onlineConfig?.lockConfig ?: legacyLockConfig
    val isLockedMode: Boolean get() = onlineConfig?.isLockedMode ?: legacyIsLockedMode ?: false
}
