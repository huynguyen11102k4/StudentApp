package com.examhub.student.omr.model

data class OmrEngineOptions(
    val densityThreshold: Float = 0.40f,
    val diffThreshold: Float = 0.20f,
    val enableScoring: Boolean = false,
    val enableDebugImage: Boolean = false,
    val useAdaptiveThreshold: Boolean = false,
    val adaptiveBlockSize: Int = 31,
    val adaptiveC: Float = 8.0f,
    val preprocessMarkers: Boolean = false,
    val preprocessPostWarp: Boolean = false,
    val morphCleanup: Boolean = false,
    val autoAdaptive: Boolean = true
) {
    companion object {
        val presetFast = OmrEngineOptions(autoAdaptive = false)

        val presetPhoto = OmrEngineOptions(
            autoAdaptive = false,
            useAdaptiveThreshold = true
        )

        val presetWrinkled = OmrEngineOptions(
            autoAdaptive = false,
            useAdaptiveThreshold = true,
            preprocessMarkers = true,
            preprocessPostWarp = true,
            morphCleanup = true
        )

        val presetAuto = OmrEngineOptions(autoAdaptive = true)
    }
}
