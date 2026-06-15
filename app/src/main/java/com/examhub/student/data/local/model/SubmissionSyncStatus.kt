package com.examhub.student.data.local.model

enum class SubmissionSyncStatus {
    PENDING_SYNC,
    UPLOADING_IMAGES,
    SYNCING,
    SYNCED,
    FAILED_CAPTURE_AFTER_DEADLINE,
    FAILED_TERMINAL
}
