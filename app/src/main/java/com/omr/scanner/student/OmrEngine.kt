package com.omr.scanner.student

import android.graphics.Bitmap
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

// ─── Result Data Classes ───────────────────────────────────────────

data class IdResult(
    val studentId: String,
    val classCode: String,
    val examCode: String,
    val ok: Boolean,
    val error: String = ""
)

data class AnswerResult(
    val questionNumber: Int,
    val answer: String,        // "A", "C", "A,C", "" (no answer)
    val flag: Int = 0          // 0=OK, 1=ambiguous/erasure
)

data class ScoreDetail(
    val questionNumber: Int,
    val studentAnswer: String,
    val correctAnswer: String,
    val isCorrect: Boolean,
    val flag: Int = 0
)

data class ScoreData(
    val totalQuestions: Int,
    val correctCount: Int,
    val totalScore: Double,
    val details: List<ScoreDetail>
)

data class OmrOutput(
    val success: Boolean,
    val errorCode: String = "",
    val errorMessage: String = "",
    val warnings: List<String> = emptyList(),
    val idResult: IdResult = IdResult("", "", "", false),
    val answers: List<AnswerResult> = emptyList(),
    val scored: Boolean = false,
    val scoreResult: ScoreData? = null,
    val laplacianVariance: Float = 0f,
    val meanBrightness: Float = 0f,
    val debugImageBase64: String = ""      // JPEG base64 (only if enableDebugImage=true)
)

// ─── Options ───────────────────────────────────────────────────────

data class OmrEngineOptions(
    val densityThreshold: Float = 0.40f,
    val diffThreshold: Float = 0.20f,
    val enableScoring: Boolean = false,
    val enableDebugImage: Boolean = false,

    // ─── Adaptive threshold ──────────────────────────────────
    /** When true: use cv::adaptiveThreshold before reading bubbles.
     *  Handles uneven lighting better than fixed 128 grayscale cutoff. */
    val useAdaptiveThreshold: Boolean = false,
    /** Block size (odd, 3-255). Larger = more stable in low-light. */
    val adaptiveBlockSize: Int = 31,
    /** Constant subtracted from mean. Higher = less sensitive to faint marks. */
    val adaptiveC: Float = 8.0f,

    // ─── Preprocessing pipeline ───────────────────────────────
    /** Bilateral + CLAHE on input before marker detection.
     *  Matches Python pipeline B1.5+B2. Better for wrinkled paper. */
    val preprocessMarkers: Boolean = false,
    /** Bilateral + GaussianBlur on warped image before threshold.
     *  Matches Python pipeline B5+B6. Reduces paper texture noise. */
    val preprocessPostWarp: Boolean = false,
    /** Morphological close+open+Otsu after adaptive threshold.
     *  Matches Python pipeline B7-B10. Removes fold/crease noise. */
    val morphCleanup: Boolean = false,

    // ─── Auto-adaptive mode ──────────────────────────────────
    /** When true: app auto-decides ALL preprocessing flags
     *  based on laplacian variance and mean brightness.
     *  Teacher just shoots — no manual tuning needed.
     *  Overrides preprocessMarkers/postWarp/morphCleanup
     *  and useAdaptiveThreshold. */
    val autoAdaptive: Boolean = true
) {
    companion object {
        /** Fastest: no preprocessing. For flatbed-scanned sheets. */
        val presetFast = OmrEngineOptions(autoAdaptive = false)

        /** Balanced: adaptive threshold only. Good default for camera. */
        val presetPhoto = OmrEngineOptions(autoAdaptive = false,
            useAdaptiveThreshold = true)

        /** Full preprocessing: bilateral+CLAHE+adaptive+morph.
         *  For wrinkled / low-light paper. */
        val presetWrinkled = OmrEngineOptions(autoAdaptive = false,
            useAdaptiveThreshold = true,
            preprocessMarkers = true, preprocessPostWarp = true,
            morphCleanup = true)

        /** Auto-adaptive: app decides. Recommended for teacher usage. */
        val presetAuto = OmrEngineOptions(autoAdaptive = true)
    }
}

// ─── Engine ────────────────────────────────────────────────────────

/**
 * OmrEngine — High-level Kotlin wrapper around the native OMR pipeline.
 *
 * Usage:
 * ```
 * val engine = OmrEngine()
 * val result = engine.process(bitmap, templateJson, answerKeyJson, OmrEngineOptions())
 * ```
 */
class OmrEngine {

    companion object {
        private const val TAG = "OmrEngine"

        /**
         * Process an OMR sheet image and extract answers.
         *
         * @param bitmap          Input image (Android Bitmap)
         * @param templateJson    Template JSON from API
         * @param answerKeyJson   Answer key JSON (optional, for scoring)
         * @param options         Processing options
         * @return OmrOutput parsed result
         */
        fun process(
            bitmap: Bitmap,
            templateJson: String,
            answerKeyJson: String? = null,
            options: OmrEngineOptions = OmrEngineOptions()
        ): OmrOutput {
            val startTime = System.currentTimeMillis()

            val resultJson = NativeLib.processOmr(
                bitmap,
                templateJson,
                answerKeyJson,
                options.densityThreshold,
                options.diffThreshold,
                options.enableScoring,
                options.enableDebugImage,
                options.useAdaptiveThreshold,
                options.adaptiveBlockSize,
                options.adaptiveC,
                options.preprocessMarkers,
                options.preprocessPostWarp,
                options.morphCleanup,
                options.autoAdaptive
            )

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "OMR processing completed in ${elapsed}ms")

            return parseResult(resultJson)
        }

        /**
         * Parse the JSON string result from native into OmrOutput.
         */
        fun parseResult(jsonStr: String): OmrOutput {
            return try {
                val json = JSONObject(jsonStr)

                val success = json.optBoolean("success", false)
                val errorCode = json.optString("error_code", "")
                val errorMessage = json.optString("error_message", "")

                // Warnings
                val warningsArr = json.optJSONArray("warnings")
                val warnings = mutableListOf<String>()
                if (warningsArr != null) {
                    for (i in 0 until warningsArr.length()) {
                        warnings.add(warningsArr.getString(i))
                    }
                }

                // ID result
                val idJson = json.optJSONObject("id_result")
                val idResult = if (idJson != null) {
                    IdResult(
                        studentId = idJson.optString("student_id", ""),
                        classCode = idJson.optString("class_code", ""),
                        examCode = idJson.optString("exam_code", ""),
                        ok = idJson.optBoolean("id_ok", false),
                        error = idJson.optString("id_error", "")
                    )
                } else {
                    IdResult("", "", "", false)
                }

                // Answers
                val answersArr = json.optJSONArray("student_answers")
                val answers = mutableListOf<AnswerResult>()
                if (answersArr != null) {
                    for (i in 0 until answersArr.length()) {
                        val aObj = answersArr.getJSONObject(i)
                        answers.add(
                            AnswerResult(
                                questionNumber = aObj.getInt("question_number"),
                                answer = aObj.optString("answer", ""),
                                flag = aObj.optInt("flag", 0)
                            )
                        )
                    }
                }

                // Scoring
                val scored = json.optBoolean("scored", false)
                val scoreResult = if (scored) {
                    val scoreJson = json.optJSONObject("score_result")
                    if (scoreJson != null) {
                        val detailsArr = scoreJson.optJSONArray("details")
                        val details = mutableListOf<ScoreDetail>()
                        if (detailsArr != null) {
                            for (i in 0 until detailsArr.length()) {
                                val dObj = detailsArr.getJSONObject(i)
                                details.add(
                                    ScoreDetail(
                                        questionNumber = dObj.getInt("question_number"),
                                        studentAnswer = dObj.optString("student_answer", ""),
                                        correctAnswer = dObj.optString("correct_answer", ""),
                                        isCorrect = dObj.getBoolean("is_correct"),
                                        flag = dObj.optInt("flag", 0)
                                    )
                                )
                            }
                        }
                        ScoreData(
                            totalQuestions = scoreJson.getInt("total_questions"),
                            correctCount = scoreJson.getInt("correct_count"),
                            totalScore = scoreJson.getDouble("total_score"),
                            details = details
                        )
                    } else null
                } else null

                OmrOutput(
                    success = success,
                    errorCode = errorCode,
                    errorMessage = errorMessage,
                    warnings = warnings,
                    idResult = idResult,
                    answers = answers,
                    scored = scored,
                    scoreResult = scoreResult,
                    laplacianVariance = json.optDouble("laplacian_variance", 0.0).toFloat(),
                    meanBrightness = json.optDouble("mean_brightness", 0.0).toFloat(),
                    debugImageBase64 = json.optString("debug_image_base64", "")
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse OMR result JSON", e)
                OmrOutput(
                    success = false,
                    errorCode = "PARSE_ERROR",
                    errorMessage = "Failed to parse native result: ${e.message}"
                )
            }
        }
    }
}
