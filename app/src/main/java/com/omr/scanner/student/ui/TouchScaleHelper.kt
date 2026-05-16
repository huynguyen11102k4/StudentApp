package com.omr.scanner.student.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator

/**
 * Material Design 3 — 3D Touch Feedback
 * Applies scale + elevation animation on press/release for a lively, tactile feel.
 *
 * Usage: view.setOnTouchListener(TouchScaleHelper())
 */
class TouchScaleHelper(
    private val scaleTo: Float = 0.96f,
    private val scaleFrom: Float = 1.0f,
    private val pressDuration: Long = 120L,
    private val releaseDuration: Long = 280L
) : View.OnTouchListener {

    private var isScaledDown = false

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isScaledDown) {
                    animateScale(v, scaleFrom, scaleTo, pressDuration)
                    isScaledDown = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isScaledDown) {
                    animateScale(v, scaleTo, scaleFrom, releaseDuration)
                    isScaledDown = false
                }
            }
        }
        return false // allow click listener + ripple to also fire
    }

    private fun animateScale(view: View, from: Float, to: Float, duration: Long) {
        view.animate().cancel()

        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, from, to)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, from, to)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            this.duration = duration
            interpolator = if (to == scaleFrom) {
                // Release: slight overshoot bounce for liveliness
                OvershootInterpolator(0.4f)
            } else {
                // Press: smooth ease-in
                android.view.animation.AccelerateInterpolator()
            }
            start()
        }
    }
}
