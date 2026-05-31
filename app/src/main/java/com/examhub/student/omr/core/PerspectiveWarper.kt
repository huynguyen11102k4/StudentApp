package com.examhub.student.omr.core

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import com.examhub.student.omr.model.WarpResult

class PerspectiveWarper {

    fun warpRegion(src:Mat, srcCorners: List<Point>, dstWidth: Int, dstHeight: Int): WarpResult {
        val dstCorners = listOf(
            Point(0.0, 0.0),
            Point(dstWidth.toDouble(), 0.0),
            Point(dstWidth.toDouble(), dstHeight.toDouble()),
            Point(0.0, dstHeight.toDouble())
        )

        val srcMat = MatOfPoint2f(*srcCorners.toTypedArray())
        val dstMat = MatOfPoint2f(*dstCorners.toTypedArray())

        val M = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        val warped = Mat()
        Imgproc.warpPerspective(src, warped, M, Size(dstWidth.toDouble(), dstHeight.toDouble()))

        return WarpResult(warped, M)
    }

    fun warpColorImage(
        srcColor: Mat,
        transformMatrix: Mat,
        dstWidth: Int,
        dstHeight: Int
    ): Mat {
        val warpedColor = Mat()
        Imgproc.warpPerspective(
            srcColor,
            warpedColor,
            transformMatrix,
            Size(dstWidth.toDouble(), dstHeight.toDouble())
        )
        return warpedColor
    }
}
