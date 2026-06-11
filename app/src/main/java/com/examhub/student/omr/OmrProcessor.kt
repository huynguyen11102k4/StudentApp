package com.examhub.student.omr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.examhub.student.R
import com.examhub.student.data.model.Answer
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.omr.core.OmrEngine
import com.examhub.student.omr.model.IdResult
import com.examhub.student.omr.model.OmrEngineOptions
import com.examhub.student.service.OfflineCacheManager
import com.examhub.student.service.TokenManager
import com.examhub.student.util.helper.parseUserProfileJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class OmrProcessor(
    private val offlineCacheManager: OfflineCacheManager,
    private val tokenManager: TokenManager,
    private val context: Context
) {
    private val gson = Gson()
    private val tag = "OmrProcessorKt"

    suspend fun process(
        bitmap: Bitmap,
        examId: String
    ): OmrProcessingResult = withContext(Dispatchers.Default) {
        require(examId.isNotBlank()) { context.getString(R.string.omr_missing_exam_id) }

        val templateJson = offlineCacheManager.getTemplate(examId)
            ?: error(context.getString(R.string.omr_missing_template))

        val normalizedTemplateJson = normalizeTemplateJson(templateJson)
        val enabledIdFields = readEnabledIdFields(normalizedTemplateJson)
        val studentIdentifierMode = readStudentIdentifierMode(templateJson)
        Log.i(
            tag,
            "Template JSON raw=${templateJson.length} normalized=${normalizedTemplateJson.length} " +
                "hasAnchors=${normalizedTemplateJson.contains("anchor_points")} " +
                "hasIdZone=${normalizedTemplateJson.contains("id_zones")} " +
                "hasAnswerZones=${normalizedTemplateJson.contains("answer_zones")} " +
                "studentIdentifierMode=${studentIdentifierMode.apiValue}"
        )

        val output = OmrEngine.process(
            bitmap = bitmap,
            templateJson = normalizedTemplateJson,
            answerKeyJson = "",
            options = OmrEngineOptions.presetAuto.copy(
                enableScoring = false,
                enableDebugImage = true
            )
        )

        if (!output.success) {
            error(output.errorMessage.ifBlank { context.getString(R.string.omr_processing_failed_format, output.errorCode) })
        }

        validateIdZone(output.idResult, enabledIdFields, studentIdentifierMode, examId)
        val studentName = resolveStudentNameForReview(output.idResult, enabledIdFields)

        val answers = output.answers.map { answer ->
            Answer(
                questionNo = answer.questionNumber,
                studentAnswer = answer.answer.ifBlank { null },
                status = if (answer.answer.isBlank()) "empty" else "unknown"
            )
        }

        OmrProcessingResult(
            examId = examId,
            sessionId = "",
            studentId = if (enabledIdFields.studentId) output.idResult.studentId else "",
            classCode = if (enabledIdFields.classCode) output.idResult.classCode.ifBlank { null } else null,
            examCode = if (enabledIdFields.examCode) output.idResult.examCode.ifBlank { null } else null,
            studentIdEnabled = enabledIdFields.studentId,
            classCodeEnabled = enabledIdFields.classCode,
            examCodeEnabled = enabledIdFields.examCode,
            idOk = isEnabledIdResultReadable(output.idResult, enabledIdFields),
            idError = output.idResult.error.ifBlank { null },
            studentName = studentName,
            answers = answers,
            totalScore = null,
            rawImageBase64 = "",
            dewarpedImageBase64 = output.dewarpedImageBase64,
            debugImageBase64 = output.debugImageBase64,
            laplacianVariance = output.laplacianVariance,
            meanBrightness = output.meanBrightness,
            warnings = output.warnings
        )
    }

    private fun normalizeTemplateJson(rawJson: String): String {
        return runCatching {
            val rawRoot = JSONObject(rawJson)
            val templateRoot = rawRoot.optJSONObject("data") ?: rawRoot
            val grid = templateRoot.optJSONObject("gridConfig")
                ?: templateRoot.optJSONObject("grid_config")
                ?: templateRoot
            val gridJson = JSONObject()

            val anchors = normalizeAnchors(
                templateRoot.optJSONArray("anchorPoints")
                    ?: templateRoot.optJSONArray("anchor_points")
                    ?: grid.optJSONArray("anchorPoints")
                    ?: grid.optJSONArray("anchor_points")
            )
            gridJson.put("anchor_points", anchors)

            val idZones = grid.optJSONObject("id_zones") ?: grid.optJSONObject("idZones")
            if (idZones != null) {
                gridJson.put("id_zones", normalizeIdZones(idZones))
            }

            val answerZones = grid.optJSONArray("answer_zones") ?: grid.optJSONArray("answerZones")
            gridJson.put("answer_zones", normalizeAnswerZones(answerZones))

            JSONObject().put("gridConfig", gridJson).toString()
        }.getOrElse {
            rawJson
        }
    }

    private fun validateIdZone(
        idResult: IdResult,
        enabledFields: EnabledIdFields,
        studentIdentifierMode: StudentIdentifierMode,
        examId: String
    ) {
        if (!enabledFields.anyEnabled()) return

        val scannedStudentId = idResult.studentId.trim()
        val scannedClassCode = idResult.classCode.trim()
        val scannedExamCode = idResult.examCode.trim()

        if (enabledFields.studentId && !isReadableCode(scannedStudentId)) {
            error(context.getString(R.string.omr_unreadable_student_code))
        }
        if (enabledFields.classCode && !isReadableCode(scannedClassCode)) {
            error(context.getString(R.string.omr_unreadable_class_code))
        }
        if (enabledFields.examCode && !isReadableCode(scannedExamCode)) {
            error(context.getString(R.string.omr_unreadable_exam_code))
        }

        val expectedStudentCodes = getExpectedStudentCodes(studentIdentifierMode)
        if (enabledFields.studentId && expectedStudentCodes.isNotEmpty() && expectedStudentCodes.none { sameCode(it, scannedStudentId) }) {
            error(context.getString(R.string.omr_student_code_mismatch_format, scannedStudentId))
        }

        if (enabledFields.classCode) {
            val classCodes = buildList {
                offlineCacheManager.getExamClassCode(examId)?.let(::add)
                addAll(offlineCacheManager.getCachedClassBasics().map { it.classCode })
            }
                .filter { it.isNotBlank() }
                .distinctBy { it.trim().uppercase() }
            if (classCodes.isNotEmpty() && classCodes.none { sameClassCode(it, scannedClassCode) }) {
                error(context.getString(R.string.omr_class_code_mismatch_format, scannedClassCode))
            }
        }

    }

    private fun isEnabledIdResultReadable(
        idResult: IdResult,
        enabledFields: EnabledIdFields
    ): Boolean {
        return (!enabledFields.studentId || isReadableCode(idResult.studentId)) &&
            (!enabledFields.classCode || isReadableCode(idResult.classCode)) &&
            (!enabledFields.examCode || isReadableCode(idResult.examCode))
    }

    private fun isReadableCode(value: String): Boolean {
        val normalized = value.trim()
        return normalized.isNotBlank() && '?' !in normalized
    }

    private fun readEnabledIdFields(templateJson: String): EnabledIdFields {
        return runCatching {
            val root = JSONObject(templateJson)
            val grid = root.optJSONObject("gridConfig") ?: root.optJSONObject("grid_config") ?: root
            val idZones = grid.optJSONObject("id_zones") ?: grid.optJSONObject("idZones")
            val items = idZones?.optJSONArray("items") ?: return@runCatching EnabledIdFields()

            var studentId = false
            var classCode = false
            var examCode = false
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                if (!item.optBoolean("enabled", true)) continue
                when (item.optString("type")) {
                    "student_id" -> studentId = true
                    "class_code" -> classCode = true
                    "exam_code" -> examCode = true
                }
            }
            EnabledIdFields(studentId, classCode, examCode)
        }.getOrDefault(EnabledIdFields())
    }

    private fun getExpectedStudentCodes(mode: StudentIdentifierMode): List<String> {
        val profile = getCachedProfile() ?: return emptyList()

        val externalCodes = listOfNotNull(profile.student?.studentCode)
        val internalCodes = listOfNotNull(profile.student?.internalId)

        return when (mode) {
            StudentIdentifierMode.EXTERNAL -> externalCodes
            StudentIdentifierMode.INTERNAL -> internalCodes
            StudentIdentifierMode.UNKNOWN -> externalCodes + internalCodes + profile.id
        }.filter { it.isNotBlank() }.distinctBy { it.trim().uppercase() }
    }

    private fun resolveStudentNameForReview(
        idResult: IdResult,
        enabledFields: EnabledIdFields
    ): String? {
        if (enabledFields.studentId && !isReadableCode(idResult.studentId)) return null
        return getCachedProfile()?.fullName?.takeIf { it.isNotBlank() }
    }

    private fun getCachedProfile(): UserResponse? {
        return tokenManager.getCachedProfileJson()
            ?.let(gson::parseUserProfileJson)
    }

    private fun readStudentIdentifierMode(rawTemplateJson: String): StudentIdentifierMode {
        return runCatching {
            val root = JSONObject(rawTemplateJson)
            val data = root.optJSONObject("data")
            val templateRoot = data ?: root
            val grid = templateRoot.optJSONObject("gridConfig")
                ?: templateRoot.optJSONObject("grid_config")
                ?: templateRoot
            val idZones = grid.optJSONObject("id_zones") ?: grid.optJSONObject("idZones")
            val studentItem = idZones?.optJSONArray("items")?.let { items ->
                (0 until items.length())
                    .mapNotNull { index -> items.optJSONObject(index) }
                    .firstOrNull { it.optString("type") == "student_id" }
            }

            listOf(
                studentItem?.findIdentifierModeValue(),
                idZones?.findIdentifierModeValue(),
                grid.findIdentifierModeValue(),
                templateRoot.findIdentifierModeValue(),
                data?.findIdentifierModeValue(),
                root.findIdentifierModeValue()
            ).firstNotNullOfOrNull { StudentIdentifierMode.from(it) } ?: StudentIdentifierMode.UNKNOWN
        }.getOrDefault(StudentIdentifierMode.UNKNOWN)
    }

    private fun JSONObject.findIdentifierModeValue(): String? {
        val keys = listOf(
            "student_code_type",
            "identification_mode",
            "source"
        )
        return keys.firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() } }
    }

    private fun sameCode(left: String, right: String): Boolean {
        return left.trim().uppercase() == right.trim().uppercase()
    }

    private fun sameClassCode(left: String, right: String): Boolean {
        val normalizedLeft = left.trim().uppercase()
        val normalizedRight = right.trim().uppercase()
        if (normalizedLeft == normalizedRight) return true
        return normalizedLeft.trimStart('0').ifBlank { "0" } ==
            normalizedRight.trimStart('0').ifBlank { "0" }
    }

    private data class EnabledIdFields(
        val studentId: Boolean = false,
        val classCode: Boolean = false,
        val examCode: Boolean = false
    ) {
        fun anyEnabled() = studentId || classCode || examCode
    }

    private enum class StudentIdentifierMode(val apiValue: String) {
        INTERNAL("internal"),
        EXTERNAL("external"),
        UNKNOWN("unknown");

        companion object {
            fun from(value: String?): StudentIdentifierMode? {
                val normalized = value
                    ?.trim()
                    ?.lowercase()
                    ?.replace("-", "_")
                    ?: return null
                return when (normalized) {
                    "internal", "internal_id", "class_internal", "class_member_internal", "member_internal", "internal_code" -> INTERNAL
                    "external", "student_code", "school_code", "external_id", "external_code", "external_code_mode" -> EXTERNAL
                    else -> null
                }
            }
        }
    }

    private fun normalizeAnchors(rawAnchors: JSONArray?): JSONArray {
        if (rawAnchors == null || rawAnchors.length() < 4) return defaultAnchorPointsJson()

        val normalized = JSONArray()
        var hasNonZeroPoint = false
        for (i in 0 until rawAnchors.length()) {
            val point = rawAnchors.optJSONObject(i) ?: continue
            val x = point.optNullableDouble("abs_x") ?: point.optNullableDouble("absX") ?: 0.0
            val y = point.optNullableDouble("abs_y") ?: point.optNullableDouble("absY") ?: 0.0
            if (x != 0.0 || y != 0.0) hasNonZeroPoint = true
            normalized.put(JSONObject().apply {
                put("id", point.optInt("id", i + 1))
                put("abs_x", x)
                put("abs_y", y)
                put("u", point.optNullableDouble("u") ?: 0.0)
                put("v", point.optNullableDouble("v") ?: 0.0)
            })
        }
        return if (normalized.length() >= 4 && hasNonZeroPoint) normalized else defaultAnchorPointsJson()
    }

    private fun normalizeIdZones(rawIdZones: JSONObject): JSONObject {
        val boundingBox = rawIdZones.optJSONObject("bounding_box")
            ?: rawIdZones.optJSONObject("boundingBox")
            ?: JSONObject()
        val items = rawIdZones.optJSONArray("items")
        return JSONObject().apply {
            put("bounding_box", normalizeBoundingBox(boundingBox))
            put("items", if (items != null && items.length() > 0) {
                JSONArray().apply {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        put(JSONObject().apply {
                            put("type", item.optString("type"))
                            put("enabled", item.optBoolean("enabled", true))
                            put("num_digits", item.optInt("num_digits", item.optInt("numDigits", 0)))
                        })
                    }
                }
            } else {
                JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "student_id")
                        put("enabled", true)
                        put("num_digits", rawIdZones.optInt("max_digits", rawIdZones.optInt("maxDigits", 8)))
                    })
                }
            })
        }
    }

    private fun normalizeAnswerZones(rawAnswerZones: JSONArray?): JSONArray {
        if (rawAnswerZones == null) return JSONArray()
        return JSONArray().apply {
            for (i in 0 until rawAnswerZones.length()) {
                val zone = rawAnswerZones.optJSONObject(i) ?: continue
                val boundingBox = zone.optJSONObject("bounding_box")
                    ?: zone.optJSONObject("boundingBox")
                    ?: JSONObject()
                val layout = zone.optJSONObject("layout")
                val startNumber = zone.optInt("start_number", zone.optInt("startNumber", 1))
                val endNumber = zone.optInt("end_number", zone.optInt("endNumber", startNumber + 19))
                put(JSONObject().apply {
                    put("zone_id", zone.optString("zone_id", zone.optString("zoneId", "answer_zone_${i + 1}")))
                    put("type", zone.optString("type", "multiple_choice_grid"))
                    put("bounding_box", normalizeBoundingBox(boundingBox))
                    put("layout", JSONObject().apply {
                        put("rows", layout?.optInt("rows") ?: (endNumber - startNumber + 1).coerceAtLeast(1))
                        put("options", layout?.optInt("options") ?: zone.optInt("options_per_question", zone.optInt("optionsPerQuestion", 4)))
                        put("has_start_number", layout?.optBoolean("has_start_number", layout.optBoolean("hasStartNumber", true)) ?: true)
                        put("has_end_number", layout?.optBoolean("has_end_number", layout.optBoolean("hasEndNumber", false)) ?: false)
                        put("start_question_index", layout?.optInt("start_question_index", layout.optInt("startQuestionIndex", startNumber)) ?: startNumber)
                        put(
                            "selectionMode",
                            layout?.optString("selectionMode")?.takeIf { it.isNotBlank() }
                                ?: layout?.optString("selection_mode")?.takeIf { it.isNotBlank() }
                                ?: "single"
                        )
                    })
                })
            }
        }
    }

    private fun normalizeBoundingBox(box: JSONObject): JSONObject {
        return JSONObject().apply {
            put("x", box.optNullableDouble("x") ?: 0.0)
            put("y", box.optNullableDouble("y") ?: 0.0)
            put("width", box.optNullableDouble("width") ?: 0.0)
            put("height", box.optNullableDouble("height") ?: 0.0)
        }
    }

    private fun JSONObject.optNullableDouble(name: String): Double? {
        return if (has(name) && !isNull(name)) optDouble(name) else null
    }

    private fun defaultAnchorPointsJson(): JSONArray {
        val pageWidth = 827f
        val pageHeight = 1169f
        val points = listOf(
            Triple(1, 45f, 45f),
            Triple(2, 782f, 45f),
            Triple(3, 782f, 1124f),
            Triple(4, 45f, 1124f),
            Triple(5, 45f, 403f),
            Triple(6, 414f, 403f),
            Triple(7, 782f, 403f),
            Triple(8, 782f, 763f),
            Triple(9, 414f, 763f),
            Triple(10, 45f, 763f),
            Triple(11, 414f, 1124f),
            Triple(12, 414f, 45f)
        )
        return JSONArray().apply {
            points.forEach { (id, x, y) ->
                put(JSONObject().apply {
                    put("id", id)
                    put("abs_x", x)
                    put("abs_y", y)
                    put("u", x / pageWidth)
                    put("v", y / pageHeight)
                })
            }
        }
    }
}
