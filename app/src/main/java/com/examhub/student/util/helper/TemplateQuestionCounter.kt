package com.examhub.student.util.helper

import org.json.JSONObject

object TemplateQuestionCounter {
    fun countFromTemplateJson(rawTemplate: String?): Int {
        val raw = rawTemplate?.takeIf { it.isNotBlank() } ?: return 0
        return runCatching {
            val root = JSONObject(raw)
            val templateRoot = root.optJSONObject("data") ?: root
            val grid = templateRoot.optJSONObject("gridConfig")
                ?: templateRoot.optJSONObject("grid_config")
                ?: templateRoot
            val zones = grid.optJSONArray("answer_zones") ?: grid.optJSONArray("answerZones")
            var totalQuestions = 0
            if (zones != null) {
                for (index in 0 until zones.length()) {
                    val zone = zones.optJSONObject(index) ?: continue
                    totalQuestions += zone.questionCount()
                }
            }
            totalQuestions
        }.getOrDefault(0)
    }

    fun countFromTemplateJsonOrDefault(rawTemplate: String?, defaultCount: Int): Int =
        if (rawTemplate.isNullOrBlank()) {
            defaultCount.coerceAtLeast(0)
        } else {
            countFromTemplateJson(rawTemplate)
        }

    private fun JSONObject.questionCount(): Int {
        val layout = optJSONObject("layout")
        val rows = layout?.optNullableInt("rows") ?: optNullableInt("rows")
        if (rows != null) return rows.coerceAtLeast(0)

        val startNumber = optNullableInt("start_number") ?: optNullableInt("startNumber") ?: return 0
        val endNumber = optNullableInt("end_number") ?: optNullableInt("endNumber") ?: return 0
        return (endNumber - startNumber + 1).coerceAtLeast(0)
    }

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null
}
