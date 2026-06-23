package com.examhub.student.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var imageBitmap: Bitmap? = null

    // Layout configuration
    private var cx = 0f
    private var cy = 0f
    private var cropRadius = 0f

    // Transformation variables
    private var scale = 1f
    private var translateX = 0f
    private var translateY = 0f

    // Constants for scale limits
    private var minScale = 1f
    private var maxScale = 4f

    // Touch event variables
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000") // Semi-transparent black overlay
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
    }

    init {
        // Required for clear paint overlay transparency to work properly (renders on offscreen buffer)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setImageBitmap(bitmap: Bitmap) {
        this.imageBitmap = bitmap
        // Reset transformations
        scale = 1f
        translateX = 0f
        translateY = 0f
        isDragging = false
        setupInitialPosition()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        cropRadius = min(w, h) * 0.4f
        setupInitialPosition()
    }

    private fun setupInitialPosition() {
        val bitmap = imageBitmap ?: return
        if (width == 0 || height == 0) return

        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()

        // Minimum scale to cover the crop circle
        val diameter = cropRadius * 2
        minScale = max(diameter / bw, diameter / bh)
        maxScale = minScale * 4f

        scale = minScale

        // Center the image initially
        translateX = cx - (bw * scale) / 2f
        translateY = cy - (bh * scale) / 2f

        clampPosition()
    }

    fun setZoom(progress: Int) {
        // progress is 0..100
        val bitmap = imageBitmap ?: return
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()

        val oldScale = scale
        val newScale = minScale + (progress / 100f) * (maxScale - minScale)
        scale = newScale

        // Zoom centered around the crop circle center (cx, cy)
        val imageCenterX = (cx - translateX) / oldScale
        val imageCenterY = (cy - translateY) / oldScale

        translateX = cx - imageCenterX * newScale
        translateY = cy - imageCenterY * newScale

        clampPosition()
        invalidate()
    }

    private fun clampPosition() {
        val bitmap = imageBitmap ?: return
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()

        val minTranslateX = cx + cropRadius - bw * scale
        val maxTranslateX = cx - cropRadius
        val minTranslateY = cy + cropRadius - bh * scale
        val maxTranslateY = cy - cropRadius

        translateX = translateX.coerceIn(minTranslateX, maxTranslateX)
        translateY = translateY.coerceIn(minTranslateY, maxTranslateY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (imageBitmap == null) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    translateX += dx
                    translateY += dy
                    clampPosition()
                    invalidate()
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = imageBitmap ?: return

        // 1. Draw image under the overlay
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scale, scale)
        canvas.drawBitmap(bitmap, 0f, 0f, imagePaint)
        canvas.restore()

        // 2. Draw dark overlay with clear circle
        // We use an offscreen canvas/layer to make transparent cutout work
        canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        canvas.drawCircle(cx, cy, cropRadius, clearPaint)
        canvas.restore()

        // 3. Draw white circle border
        canvas.drawCircle(cx, cy, cropRadius, borderPaint)
    }

    fun cropImage(): Bitmap? {
        val bitmap = imageBitmap ?: return null

        // Calculate source rectangle in the original bitmap
        val srcLeft = (cx - cropRadius - translateX) / scale
        val srcTop = (cy - cropRadius - translateY) / scale
        val srcSize = (cropRadius * 2) / scale

        // Clamp source rectangle to original bitmap size
        val left = srcLeft.toInt().coerceIn(0, bitmap.width)
        val top = srcTop.toInt().coerceIn(0, bitmap.height)
        val right = (srcLeft + srcSize).toInt().coerceIn(0, bitmap.width)
        val bottom = (srcTop + srcSize).toInt().coerceIn(0, bitmap.height)
        if (right <= left || bottom <= top) return null

        val srcRect = Rect(left, top, right, bottom)
        val destRect = Rect(0, 0, 400, 400)

        val cropped = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cropped)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, srcRect, destRect, paint)

        return cropped
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
