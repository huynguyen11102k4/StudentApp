package com.examhub.student

import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo

class ImagePreprocessor {
    
    fun applyBilateralFilter(src: Mat, dst: Mat, d: Int = 9, sigmaColor: Double = 25.0, sigmaSpace: Double = 75.0) {
        Imgproc.bilateralFilter(src, dst, d, sigmaColor, sigmaSpace)
    }
    
    fun applyCLAHE(src: Mat, dst: Mat, clipLimit: Double = 4.0, tileGridSize: Size = Size(4.0, 4.0)) {
        val clahe = Imgproc.createCLAHE(clipLimit, tileGridSize)
        clahe.apply(src, dst)
    }
    
    fun applyGaussianBlur(src: Mat, dst: Mat, ksize: Size = Size(3.0, 3.0), sigmaX: Double = 0.0) {
        Imgproc.GaussianBlur(src, dst, ksize, sigmaX)
    }
    
    fun preprocessForMarkerDetection(srcGray: Mat): Mat {
        val bilateral = Mat()
        val clahe = Mat()
        
        applyBilateralFilter(srcGray, bilateral)
        
        applyCLAHE(bilateral, clahe)
        
        bilateral.release()
        
        return clahe
    }
    
    fun preprocessForThreshold(srcWarped: Mat): Mat {
        val bilateral2 = Mat()
        val blurred = Mat()
        
        applyBilateralFilter(srcWarped, bilateral2, d = 5, sigmaColor = 50.0, sigmaSpace = 50.0)
        
        applyGaussianBlur(bilateral2, blurred, Size(3.0, 3.0), 0.0)
        
        bilateral2.release()
        
        return blurred
    }
}
