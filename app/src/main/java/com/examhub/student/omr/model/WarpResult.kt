package com.examhub.student.omr.model

import org.opencv.core.Mat

data class WarpResult(
    val warpedImage: Mat,
    val transformMatrix: Mat
)
