package com.examhub.student.omr.core

import com.examhub.student.omr.model.MarkerInfo
import com.examhub.student.omr.nativebridge.NativeLib
import org.opencv.core.Mat
import org.opencv.core.Point

class DewarpProcessor {
    enum class DewarpMethod {
        NONE,
        HYBRID,
        MESH,
        TPS
    }

    private fun getSmoothParam(severity: Int): Double {
        return when (severity) {
            2 -> 1.5
            1 -> 0.8
            else -> 0.3
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

        return when (method) {
            DewarpMethod.NONE -> src.clone()
            DewarpMethod.HYBRID -> {
                val result = Mat()
                NativeLib.hybridWarp(
                    src.nativeObjAddr,
                    result.nativeObjAddr,
                    detectedMarkers.mapValues { it.value.center },
                    expectedMarkers,
                    width,
                    height
                )
                result
            }
            DewarpMethod.MESH -> {
                val result = Mat()
                NativeLib.meshWarp(
                    src.nativeObjAddr,
                    result.nativeObjAddr,
                    detectedMarkers.mapValues { it.value.center },
                    expectedMarkers,
                    width,
                    height,
                    20,
                    smoothParam
                )
                result
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
                    smoothParam
                )
                result
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
        val analysis = NativeLib.analyzeDistortion(detectedMarkers.mapValues { it.value.center }, expectedMarkers)
        val method = when (analysis.severity) {
            0 -> DewarpMethod.NONE
            1 -> DewarpMethod.HYBRID
            2 -> DewarpMethod.TPS
            else -> DewarpMethod.NONE
        }
        return dewarp(src, detectedMarkers, expectedMarkers, method, analysis.severity, width, height)
    }
}
