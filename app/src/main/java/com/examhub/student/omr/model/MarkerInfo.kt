package com.examhub.student.omr.model

import org.opencv.core.Point

data class MarkerInfo(
    val center: Point,
    val corners: List<Point>
)
