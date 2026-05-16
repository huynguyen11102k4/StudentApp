package com.omr.scanner.student.ui.cameraar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
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
    private val onCaptureFailed: (Throwable) -> Unit = {}
) {
    companion object {
        private const val TAG = "CameraManager"
    }

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var isTakingPicture = false

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
        cameraProviderFuture.addListener({
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

        try {
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            cameraControl = camera?.cameraControl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
            onCaptureFailed(e)
        }
    }

    fun capturePhoto(): Boolean {
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
                        onCaptureFailed(IllegalStateException("Không đọc được ảnh vừa chụp"))
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
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = flashMode
        return when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> "off"
            ImageCapture.FLASH_MODE_ON -> "on"
            else -> "auto"
        }
    }

    fun isFlashAvailable(): Boolean = camera?.cameraInfo?.hasFlashUnit() == true

    fun setLensFacing(facing: Int) {
        lensFacing = facing
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
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
