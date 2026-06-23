package com.examhub.student.util.helper

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TemplateQuestionCounterTest {
    @Test
    fun rowsZeroContributesNoQuestionsEvenWhenStartEndExist() {
        val template = """
            {
              "gridConfig": {
                "answer_zones": [
                  {
                    "start_number": 1,
                    "end_number": 20,
                    "layout": { "rows": 0 }
                  },
                  {
                    "start_number": 21,
                    "end_number": 30,
                    "layout": { "rows": 10 }
                  }
                ]
              }
            }
        """.trimIndent()

        assertEquals(10, TemplateQuestionCounter.countFromTemplateJson(template))
    }

    @Test
    fun missingRowsFallsBackToInclusiveQuestionRange() {
        val template = """
            {
              "data": {
                "grid_config": {
                  "answerZones": [
                    { "startNumber": 3, "endNumber": 7 }
                  ]
                }
              }
            }
        """.trimIndent()

        assertEquals(5, TemplateQuestionCounter.countFromTemplateJson(template))
    }

    @Test
    fun defaultCountDoesNotOverrideExplicitZeroRowsTemplate() {
        val template = """
            {
              "gridConfig": {
                "answer_zones": [
                  { "start_number": 1, "end_number": 20, "rows": 0 }
                ]
              }
            }
        """.trimIndent()

        assertEquals(0, TemplateQuestionCounter.countFromTemplateJsonOrDefault(template, 20))
    }
}
