package com.examhub.student.omr

class OmrReviewStore {
    private var latestResult: OmrProcessingResult? = null

    fun save(result: OmrProcessingResult) {
        latestResult = result
    }

    fun consume(): OmrProcessingResult? {
        val result = latestResult
        latestResult = null
        return result
    }
}
