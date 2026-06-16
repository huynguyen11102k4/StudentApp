package com.examhub.student.model.response.submission

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class StudentSubmitResponseTest {
    @Test
    fun parsesCamelCaseNavigationFields() {
        val response = Gson().fromJson(
            """
            {
              "submissionId": "submission-1",
              "sheetId": "sheet-1",
              "submissionStatus": "PENDING_GRADING",
              "sessionStatus": "SUBMITTED"
            }
            """.trimIndent(),
            StudentSubmitResponse::class.java
        )

        assertEquals("submission-1", response.submissionId)
        assertEquals("sheet-1", response.resultId)
        assertEquals("PENDING_GRADING", response.status)
        assertEquals("SUBMITTED", response.sessionStatus)
    }
}
