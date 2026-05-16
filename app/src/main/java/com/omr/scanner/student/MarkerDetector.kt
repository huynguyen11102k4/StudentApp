package com.omr.scanner.student

import android.util.Log
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Dictionary
import org.opencv.objdetect.Objdetect
import kotlin.collections.HashMap

data class MarkerInfo(
    val center: Point,
    val corners: List<Point>
)

class MarkerDetector {
    private val arucoDict: Dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_APRILTAG_16h5)
    private val detectorParams: DetectorParameters = DetectorParameters()
    private val arucoDetector: ArucoDetector = ArucoDetector(arucoDict, detectorParams)

    fun findArucoMarkers(imgGray: Mat): HashMap<Int, MarkerInfo>{
        val corners = ArrayList<Mat>()
        val ids = Mat()
        val rejected = ArrayList<Mat>()

        arucoDetector.detectMarkers(imgGray, corners, ids, rejected)

        val numMarkers = corners.size
        Log.d("MarkerDetector", "Found $numMarkers Aruco Markers")

        if(numMarkers < 4){
            throw RuntimeException("Not enough markers found, Only found: $numMarkers")
        }

        val markerDict = HashMap<Int, MarkerInfo>()

        for(i in 0 until numMarkers){
            val corner = corners[i]
            val markerId = ids.get(i, 0)[0].toInt()

            val cornersList = mutableListOf<Point>()
            for(j in 0..3){
                val x = corner.get(j, 0)[0]
                val y = corner.get(j, 0)[1]
                cornersList.add(Point(x, y))
            }
            val centerX = cornersList.map { it.x }.average()
            val centerY = cornersList.map { it.y }.average()
            val center = Point(centerX, centerY)

            markerDict[markerId] = MarkerInfo(center, cornersList)
            Log.d("MarkerDetector", "Marker $markerId at $center")
        }
        return markerDict
    }

    fun getMainCorners(markerDict: HashMap<Int, MarkerInfo>): List<Point> {
        val requiredIds = listOf(1, 3, 2, 4)

        val missingIds = requiredIds.filter { !markerDict.containsKey(it) }
        if (missingIds.isNotEmpty()) {
            throw RuntimeException("Missing required corner markers: $missingIds")
        }

        val tl = markerDict[1]!!.center  // Top-Left
        val tr = markerDict[3]!!.center  // Top-Right
        val bl = markerDict[2]!!.center  // Bottom-Left
        val br = markerDict[4]!!.center  // Bottom-Right

        Log.d("MarkerDetector", "TL: $tl, TR: $tr, BL: $bl, BR: $br")

        return listOf(tl, tr, br, bl)
    }

    fun drawMarkerIds(img: Mat, markerDict: HashMap<Int, MarkerInfo>): Mat {
        val imgVis = img.clone()

        for ((markerId, markerInfo) in markerDict) {
            val corners = markerInfo.corners
            val center = markerInfo.center

            val matOfPoints = MatOfPoint(*corners.toTypedArray())

            val contours = listOf(matOfPoints)
            Imgproc.drawContours(imgVis, contours, 0, org.opencv.core.Scalar(0.0, 255.0, 0.0), 2)

            Imgproc.circle(imgVis, center, 6, org.opencv.core.Scalar(255.0, 0.0, 0.0), -1)

            Imgproc.putText(
                imgVis,
                markerId.toString(),
                Point(center.x - 15, center.y + 15),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                1.2,
                org.opencv.core.Scalar(0.0, 255.0, 0.0),
                3
            )
        }

        return imgVis
    }
}

private class MatOfPoint : Mat() {
    fun fromList(points: List<Point>) {
        val pointArray = points.toTypedArray()
        val matOfPoint2f = org.opencv.core.MatOfPoint2f(*pointArray)
        matOfPoint2f.convertTo(this, org.opencv.core.CvType.CV_32S)
    }
}
