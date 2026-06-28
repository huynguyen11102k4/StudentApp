package com.examhub.student.omr.nativebridge

import org.opencv.core.Point
import com.examhub.student.omr.model.DistortionResult

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
            requiredMarkers: Int = 12,
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
