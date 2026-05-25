package com.examhub.student.extension

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.OvershootInterpolator
import com.examhub.student.ui.TouchScaleHelper

/**
 * Extension functions for applying MD3 3D micro-interactions to any View.
 */

/** Default press scale: 96% */
fun View.pressScale(scaleDown: Float = 0.96f) {
    animate().cancel()
    AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@pressScale, View.SCALE_X, 1f, scaleDown),
            ObjectAnimator.ofFloat(this@pressScale, View.SCALE_Y, 1f, scaleDown)
        )
        duration = 120
        start()
    }
}

/** Release scale with bounce back to 100% */
fun View.releaseScale() {
    animate().cancel()
    AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@releaseScale, View.SCALE_X, this@releaseScale.scaleX, 1f),
            ObjectAnimator.ofFloat(this@releaseScale, View.SCALE_Y, this@releaseScale.scaleY, 1f)
        )
        duration = 280
        interpolator = OvershootInterpolator(0.4f)
        start()
    }
}

/**
 * Apply 3D touch feedback to any View.
 * Call this once in onViewCreated.
 *
 * Usage: myCard.add3DTouch()
 */
fun View.add3DTouch(scaleTo: Float = 0.96f) {
    this.setOnTouchListener(TouchScaleHelper(scaleTo = scaleTo))
}
