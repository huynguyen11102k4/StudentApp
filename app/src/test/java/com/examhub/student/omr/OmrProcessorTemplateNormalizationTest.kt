package com.examhub.student.omr

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sun.misc.Unsafe

@RunWith(RobolectricTestRunner::class)
class OmrProcessorTemplateNormalizationTest {
    @Test
    fun emptyIdZoneItemsAreNotBackfilledFromMaxDigits() {
        val normalized = normalizeTemplate(
            """
            {
              "gridConfig": {
                "id_zones": {
                  "max_digits": 8,
                  "bounding_box": { "x": 389, "y": 80, "width": 418, "height": 288 },
                  "items": []
                },
                "answer_zones": []
              }
            }
            """.trimIndent()
        )

        val items = JSONObject(normalized)
            .getJSONObject("gridConfig")
            .getJSONObject("id_zones")
            .getJSONArray("items")

        assertEquals(0, items.length())
        assertFalse(normalized.contains("student_id"))
    }

    @Test
    fun partialIdentifierItemsArePreservedWithoutAddingStudentId() {
        val normalized = normalizeTemplate(
            """
            {
              "gridConfig": {
                "idZones": {
                  "boundingBox": { "x": 389, "y": 80, "width": 418, "height": 288 },
                  "items": [
                    { "type": "class_code", "enabled": true, "numDigits": 4 }
                  ]
                },
                "answerZones": []
              }
            }
            """.trimIndent()
        )

        val items = JSONObject(normalized)
            .getJSONObject("gridConfig")
            .getJSONObject("id_zones")
            .getJSONArray("items")

        assertEquals(1, items.length())
        assertEquals("class_code", items.getJSONObject(0).getString("type"))
        assertEquals(4, items.getJSONObject(0).getInt("num_digits"))
        assertFalse(normalized.contains("student_id"))
    }

    private fun normalizeTemplate(raw: String): String {
        val processor = unsafe().allocateInstance(OmrProcessor::class.java) as OmrProcessor
        val method = OmrProcessor::class.java.getDeclaredMethod("normalizeTemplateJson", String::class.java)
        method.isAccessible = true
        return method.invoke(processor, raw) as String
    }

    private fun unsafe(): Unsafe {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        return field.get(null) as Unsafe
    }
}
