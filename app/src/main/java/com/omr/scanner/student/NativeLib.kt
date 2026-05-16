package com.omr.scanner.student

import org.opencv.core.Mat
import org.opencv.core.Point

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

class NativeLib {

    companion object {
        init {
            System.loadLibrary("omr_native")
        }

        @JvmStatic
        external fun analyzeDistortion(
            detectedMarkers: Map<Int, Point>,
            expectedMarkers: Map<Int, Point>
        ): DistortionResult

        @JvmStatic
        external fun tpsWarp(
            srcAddr: Long,
            dstAddr: Long,
            srcPoints: List<Point>,
            dstPoints: List<Point>,
            width: Int,
            height: Int,
            smoothness: Double = 0.8
        )

        @JvmStatic
        external fun meshWarp(
            srcAddr: Long,
            dstAddr: Long,
            detectedMarkers: Map<Int, Point>,
            expectedMarkers: Map<Int, Point>,
            width: Int,
            height: Int,
            gridSize: Int = 20,
            smoothness: Double = 0.8
        )

        @JvmStatic
        external fun hybridWarp(
            srcAddr: Long,
            dstAddr: Long,
            detectedMarkers: Map<Int, Point>,
            expectedMarkers: Map<Int, Point>,
            width: Int,
            height: Int,
            blendMargin: Int = 30
        )

        @JvmStatic
        external fun releaseMat(matAddr: Long)

        // ═══ NEW: Full OMR Processing Pipeline ═══
        @JvmStatic
        external fun processOmr(
            bitmap: android.graphics.Bitmap,
            templateJson: String,
            answerKeyJson: String?,
            densityThresh: Float,
            diffThresh: Float,
            enableScoring: Boolean,
            enableDebug: Boolean,
            useAdaptive: Boolean,
            adaptiveBlockSize: Int = 31,
            adaptiveC: Float = 8.0f,
            preMarker: Boolean = false,
            postWarp: Boolean = false,
            morphCleanup: Boolean = false,
            autoAdaptive: Boolean = true   // ← NEW: mặc định true
        ): String

        @JvmStatic
        external fun bitmapToNativeMat(bitmap: android.graphics.Bitmap): Long

        @JvmStatic
        external fun matToJpegBase64(matAddr: Long, quality: Int = 85): String

        @JvmStatic
        external fun getNativeMatAddr(matAddr: Long): Long

        /** Clear the C++ layout cache (call after template update). */
        @JvmStatic
        external fun clearOmrCache()
    }
}

class DewarpProcessor {

    enum class DewarpMethod {
        NONE,
        HYBRID,
        MESH,
        TPS
    }

    // Tính toán độ mượt tự động dựa trên mức độ biến dạng
    private fun getSmoothParam(severity: Int): Double {
        return when (severity) {
            2 -> 1.5 // SEVERE
            1 -> 0.8 // MODERATE
            else -> 0.3 // GOOD
        }
    }

    fun dewarp(
        src: Mat,
        detectedMarkers: HashMap<Int, MarkerInfo>,
        expectedMarkers: Map<Int, Point>,
        method: DewarpMethod,
        severity: Int,
        width: Int,
        height: Int
    ): Mat {
        val smoothParam = getSmoothParam(severity)

        when (method) {
            DewarpMethod.NONE -> {
                return src.clone()
            }

            DewarpMethod.HYBRID -> {
                val detectedMap = detectedMarkers.mapValues { it.value.center }
                val result = Mat()

                // Truyền result.nativeObjAddr vào làm tham số thứ 2
                NativeLib.hybridWarp(
                    src.nativeObjAddr,
                    result.nativeObjAddr,
                    detectedMap,
                    expectedMarkers,
                    width,
                    height
                )
                return result
            }

            DewarpMethod.MESH -> {
                val detectedMap = detectedMarkers.mapValues { it.value.center }
                val result = Mat()

                NativeLib.meshWarp(
                    src.nativeObjAddr,
                    result.nativeObjAddr,
                    detectedMap,
                    expectedMarkers,
                    width,
                    height,
                    20,
                    smoothParam // Truyền tham số mượt tự động
                )
                return result
            }

            DewarpMethod.TPS -> {
                val srcPoints = mutableListOf<Point>()
                val dstPoints = mutableListOf<Point>()

                for ((id, markerInfo) in detectedMarkers) {
                    if (expectedMarkers.containsKey(id)) {
                        srcPoints.add(markerInfo.center)
                        dstPoints.add(expectedMarkers[id]!!)
                    }
                }

                val result = Mat()

                NativeLib.tpsWarp(
                    src.nativeObjAddr,
                    result.nativeObjAddr,
                    srcPoints,
                    dstPoints,
                    width,
                    height,
                    smoothParam // Truyền tham số mượt tự động
                )
                return result
            }
        }
    }

    fun dewarpAuto(
        src: Mat,
        detectedMarkers: HashMap<Int, MarkerInfo>,
        expectedMarkers: Map<Int, Point>,
        width: Int,
        height: Int
    ): Mat {
        val detectedMap = detectedMarkers.mapValues { it.value.center }
        val analysis = NativeLib.analyzeDistortion(detectedMap, expectedMarkers)

        println("Distortion Analysis:")
        println("  RMS Error: ${analysis.rmsError}")
        println("  Severity: ${analysis.getSeverityString()}")
        println("  Recommendation: ${analysis.recommendation}")

        val method = when (analysis.severity) {
            0 -> DewarpMethod.NONE      // GOOD
            1 -> DewarpMethod.HYBRID    // MODERATE
            2 -> DewarpMethod.TPS       // SEVERE
            else -> DewarpMethod.NONE
        }

        println("  Selected method: $method")

        return dewarp(src, detectedMarkers, expectedMarkers, method, analysis.severity, width, height)
    }
}