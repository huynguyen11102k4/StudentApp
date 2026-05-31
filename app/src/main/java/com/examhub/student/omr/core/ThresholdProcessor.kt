package com.examhub.student.omr.core

import org.opencv.core.*
import org.opencv.imgproc.Imgproc


class ThresholdProcessor {
    fun applyAdaptiveThreshold(
        src: Mat,
        maxValue: Double = 255.0,
        adaptiveMethod: Int = Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
        thresholdType: Int = Imgproc.THRESH_BINARY,
        blockSize: Int = 251,
        C: Double = 5.0
    ): Mat {
        val thresh = Mat()
        Imgproc.adaptiveThreshold(src, thresh, maxValue, adaptiveMethod, thresholdType, blockSize, C)
        return thresh
    }

    fun applyMorphologicalCleanup(src: Mat, kernelSize: Size = Size(3.0, 3.0)): Mat {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, kernelSize)
        val temp = Mat()
        val result = Mat()
        
        Imgproc.morphologyEx(src, temp, Imgproc.MORPH_CLOSE, kernel, Point(-1.0, -1.0), 1)
        
        Imgproc.morphologyEx(temp, result, Imgproc.MORPH_OPEN, kernel, Point(-1.0, -1.0), 1)
        
        kernel.release()
        temp.release()
        
        return result
    }

    fun applyOtsuThreshold(src: Mat): Mat {
        val thresh = Mat()
        Imgproc.threshold(src, thresh, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)
        
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(2.0, 2.0))
        val result = Mat()
        Imgproc.morphologyEx(thresh, result, Imgproc.MORPH_CLOSE, kernel, Point(-1.0, -1.0), 1)
        
        kernel.release()
        thresh.release()
        
        return result
    }

    fun processThreshold(blurred: Mat): Mat {
        val adaptiveThresh = applyAdaptiveThreshold(blurred)
        
        val morphCleaned = applyMorphologicalCleanup(adaptiveThresh)
        adaptiveThresh.release()
        
        val finalThresh = applyOtsuThreshold(morphCleaned)
        morphCleaned.release()
        
        return finalThresh
    }
}
