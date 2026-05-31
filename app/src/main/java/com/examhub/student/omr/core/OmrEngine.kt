package com.examhub.student.omr.core

import android.graphics.Bitmap
import android.util.Log
import com.examhub.student.omr.model.AnswerResult
import com.examhub.student.omr.model.IdResult
import com.examhub.student.omr.model.OmrEngineOptions
import com.examhub.student.omr.model.OmrOutput
import com.examhub.student.omr.model.ScoreData
import com.examhub.student.omr.model.ScoreDetail
import com.examhub.student.omr.nativebridge.NativeLib
import org.json.JSONObject
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
                    dewarpedImageBase64 = json.optString("dewarped_image_base64", ""),
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
