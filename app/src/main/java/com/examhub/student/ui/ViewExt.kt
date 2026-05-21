package com.examhub.student.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Applies system window insets (status bar + navigation bar) to a view's padding.
 * Use for toolbars/headers to avoid overlapping with system bars.
 *
 * Usage in Fragment onViewCreated:
 *   binding.toolbar.applySystemWindowInsets(top = true)
 *   binding.root.applySystemWindowInsets(bottom = true)
 */
fun View.applySystemWindowInsets(
    top: Boolean = false,
    bottom: Boolean = false,
    left: Boolean = false,
    right: Boolean = false
) {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val newLeft = initialLeft + if (left) systemBars.left else 0
        val newTop = initialTop + if (top) systemBars.top else 0
        val newRight = initialRight + if (right) systemBars.right else 0
        val newBottom = initialBottom + if (bottom) systemBars.bottom else 0
        view.setPadding(newLeft, newTop, newRight, newBottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
