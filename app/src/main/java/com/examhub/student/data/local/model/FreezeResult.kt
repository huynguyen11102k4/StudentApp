package com.examhub.student.data.local.model

import com.examhub.student.model.response.submission.StudentSubmitResponse

sealed interface FreezeResult {
    val clientSubmissionId: String

    data class Synced(
        override val clientSubmissionId: String,
        val response: StudentSubmitResponse
    ) : FreezeResult

    data class Pending(
        override val clientSubmissionId: String
    ) : FreezeResult

    data class TerminalFailure(
        override val clientSubmissionId: String,
        val code: String
    ) : FreezeResult
}
