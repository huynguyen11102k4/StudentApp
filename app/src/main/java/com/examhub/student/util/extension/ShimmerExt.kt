package com.examhub.student.util.extension

import android.animation.ObjectAnimator
import android.view.View
import com.examhub.student.R

fun View.showShimmer(show: Boolean) {
    if (show) {
        visibility = View.VISIBLE
        if (getTag(R.id.tag_shimmer_animator) == null) {
            alpha = 0.55f
            val animator = ObjectAnimator.ofFloat(this, View.ALPHA, 0.55f, 1f).apply {
                duration = 850L
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
            setTag(R.id.tag_shimmer_animator, animator)
        }
    } else {
        (getTag(R.id.tag_shimmer_animator) as? ObjectAnimator)?.cancel()
        setTag(R.id.tag_shimmer_animator, null)
        alpha = 1f
        visibility = View.GONE
    }
}
