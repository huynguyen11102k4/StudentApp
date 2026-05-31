package com.examhub.student.omr.model

data class DistortionResult(
    val rmsError: Double,
    val maxDeviation: Double,
    val severity: Int,  // 0=GOOD, 1=MODERATE, 2=SEVERE
    val recommendation: String
) {
    fun getSeverityString(): String {
        return when (severity) {
            0 -> "GOOD"
            1 -> "MODERATE"
            2 -> "SEVERE"
            else -> "UNKNOWN"
        }
    }
}
