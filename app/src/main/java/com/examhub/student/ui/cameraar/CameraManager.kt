package com.examhub.student.ui.cameraar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.examhub.student.R
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect
import java.io.ByteArrayOutputStream
import java.lang.IllegalStateException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Helper class that manages CameraX lifecycle (Preview + ImageCapture + ImageAnalysis).
 */
class CameraManager(
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onImageCaptured: (Bitmap) -> Unit,
    private val onCaptureFailed: (Throwable) -> Unit = {},
    private val onMarkersDetected: (detected: Int, expected: Int) -> Unit = { _, _ -> },
    private val onAutoCaptureReady: () -> Boolean = { true },
    private val onCameraBound: (flashAvailable: Boolean) -> Unit = {}
) {
    companion object {
        private const val TAG = "CameraManager"
        private const val EXPECTED_MARKERS = 12
        private const val STABLE_FRAMES_FOR_AUTO_CAPTURE = 2
        private const val ANALYSIS_THROTTLE_MS = 120L
        private const val FULL_MARKER_FRAME_MAX_AGE_MS = 900L
        private const val AUTO_CAPTURE_COOLDOWN_MS = 4_000L
        @Volatile private var openCvLoaded = false
    }

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var torchEnabled = false
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    @Volatile private var isTakingPicture = false
    private var stableFullMarkerFrames = 0
    private var lastAnalysisAt = 0L
    private var lastFullMarkerAt = 0L
    private var lastAutoCaptureAt = 0L
    private var arucoDetector: ArucoDetector? = null
    @Volatile private var isShutdown = false

    fun startCamera() {
        isShutdown = false
        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
        cameraProviderFuture.addListener({
            if (isShutdown) return@addListener
            val cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        this.cameraProvider = cameraProvider
        cameraProvider.unbindAll()

        // Camera selector
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        // Preview
        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { image ->
                    analyzeMarkers(image)
                }
            }

        try {
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis
            )
            cameraControl = camera?.cameraControl
            if (isFlashAvailable()) {
                cameraControl?.enableTorch(torchEnabled)
            }
            onCameraBound(isFlashAvailable())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
            onCaptureFailed(e)
        }
    }

    fun capturePhoto(): Boolean {
        return captureHighResolutionPhoto(requireMarkerReadiness = true)
    }

    private fun captureHighResolutionPhoto(requireMarkerReadiness: Boolean): Boolean {
        if (requireMarkerReadiness && !hasRecentFullMarkerFrame()) return false
        val capture = imageCapture ?: return false
        if (isTakingPicture) return false
        isTakingPicture = true
        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    if (bitmap == null) {
                        isTakingPicture = false
                        onCaptureFailed(IllegalStateException(previewView.context.getString(R.string.camera_ar_capture_read_failed)))
                    } else {
                        isTakingPicture = false
                        onImageCaptured(bitmap)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isTakingPicture = false
                    Log.e(TAG, "Photo capture failed", exception)
                    onCaptureFailed(exception)
                }
            }
        )
        return true
    }

    fun toggleFlash(): String {
        if (!isFlashAvailable()) {
            torchEnabled = false
            imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
            return "off"
        }

        torchEnabled = !torchEnabled
        flashMode = if (torchEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        imageCapture?.flashMode = flashMode
        cameraControl?.enableTorch(torchEnabled)
        return if (torchEnabled) "on" else "off"
    }

    fun turnFlashOff(): String {
        torchEnabled = false
        flashMode = ImageCapture.FLASH_MODE_OFF
        imageCapture?.flashMode = flashMode
        cameraControl?.enableTorch(false)
        return "off"
    }

    fun isFlashAvailable(): Boolean = camera?.cameraInfo?.hasFlashUnit() == true

    fun setLensFacing(facing: Int) {
        lensFacing = facing
    }

    fun shutdown() {
        isShutdown = true
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    fun hasRecentFullMarkerFrame(): Boolean {
        return System.currentTimeMillis() - lastFullMarkerAt <= FULL_MARKER_FRAME_MAX_AGE_MS
    }

    private fun analyzeMarkers(image: ImageProxy) {
        try {
            val detector = getArucoDetector() ?: return
            val now = System.currentTimeMillis()
            if (now - lastAnalysisAt < ANALYSIS_THROTTLE_MS || isTakingPicture) return
            lastAnalysisAt = now

            val gray = imageProxyToGrayMat(image) ?: return
            val corners = ArrayList<Mat>()
            val ids = Mat()
            val rejected = ArrayList<Mat>()
            try {
                detector.detectMarkers(gray, corners, ids, rejected)
                val detected = countUniqueMarkerIds(ids)
                ContextCompat.getMainExecutor(previewView.context).execute {
                    onMarkersDetected(detected, EXPECTED_MARKERS)
                }

                if (detected >= EXPECTED_MARKERS) {
                    stableFullMarkerFrames += 1
                    lastFullMarkerAt = now
                } else {
                    stableFullMarkerFrames = 0
                }

                if (
                    stableFullMarkerFrames >= STABLE_FRAMES_FOR_AUTO_CAPTURE &&
                    now - lastAutoCaptureAt >= AUTO_CAPTURE_COOLDOWN_MS
                ) {
                    stableFullMarkerFrames = 0
                    lastAutoCaptureAt = now
                    ContextCompat.getMainExecutor(previewView.context).execute {
                        val accepted = onAutoCaptureReady()
                        if (accepted && !captureHighResolutionPhoto(requireMarkerReadiness = true)) {
                            onCaptureFailed(IllegalStateException(previewView.context.getString(R.string.camera_ar_capture_read_failed)))
                        }
                    }
                }
            } finally {
                gray.release()
                ids.release()
                corners.forEach { it.release() }
                rejected.forEach { it.release() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Marker analysis failed", e)
        } finally {
            image.close()
        }
    }

    private fun countUniqueMarkerIds(ids: Mat): Int {
        val uniqueIds = mutableSetOf<Int>()
        for (row in 0 until ids.rows()) {
            for (col in 0 until ids.cols()) {
                ids.get(row, col)?.firstOrNull()?.let { value ->
                    uniqueIds.add(value.toInt())
                }
            }
        }
        return uniqueIds.size
    }

    private fun getArucoDetector(): ArucoDetector? {
        arucoDetector?.let { return it }
        if (!openCvLoaded) {
            openCvLoaded = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
            if (!openCvLoaded) {
                Log.w(TAG, "OpenCV native library is not loaded; live marker detection disabled")
                return null
            }
        }
        return runCatching {
            val params = DetectorParameters().apply {
                set_adaptiveThreshWinSizeMin(3)
                set_adaptiveThreshWinSizeMax(53)
                set_adaptiveThreshWinSizeStep(8)
                set_adaptiveThreshConstant(5.0)
                set_minMarkerPerimeterRate(0.015)
                set_maxMarkerPerimeterRate(4.0)
                set_minCornerDistanceRate(0.03)
                set_minOtsuStdDev(3.0)
                set_errorCorrectionRate(0.8)
                set_cornerRefinementMethod(Objdetect.CORNER_REFINE_SUBPIX)
                set_cornerRefinementWinSize(5)
                set_cornerRefinementMaxIterations(30)
                set_cornerRefinementMinAccuracy(0.01)
            }
            ArucoDetector(
                Objdetect.getPredefinedDictionary(Objdetect.DICT_APRILTAG_16h5),
                params
            )
        }.onSuccess {
            arucoDetector = it
        }.onFailure {
            Log.w(TAG, "Failed to initialize ArUco detector", it)
        }.getOrNull()
    }

    private fun imageProxyToGrayMat(image: ImageProxy): Mat? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        buffer.rewind()
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val mat = Mat(height, width, CvType.CV_8UC1)

        return try {
            val row = ByteArray(width)
            for (y in 0 until height) {
                val rowStart = y * rowStride
                if (rowStart >= buffer.limit()) {
                    mat.release()
                    return null
                }
                buffer.position(rowStart)
                val bytesToRead = minOf(width, buffer.remaining())
                if (bytesToRead < width) {
                    mat.release()
                    return null
                }
                buffer.get(row, 0, width)
                mat.put(y, 0, row)
            }
            mat
        } catch (e: Exception) {
            mat.release()
            null
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val bitmap = when (image.format) {
                ImageFormat.JPEG -> {
                    val buffer = image.planes.firstOrNull()?.buffer ?: return null
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                ImageFormat.YUV_420_888 -> yuv420ToBitmap(image)
                else -> {
                    Log.w(TAG, "Unsupported image format: ${image.format}")
                    null
                }
            } ?: return null

            // Rotate based on image rotation
            if (image.imageInfo.rotationDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    .also { rotated ->
                        if (rotated !== bitmap) bitmap.recycle()
                    }
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert ImageProxy to Bitmap", e)
            null
        }
    }

    private fun yuv420ToBitmap(image: ImageProxy): Bitmap? {
        val planes = image.planes
        if (planes.size < 3) return null

        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 90, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}
