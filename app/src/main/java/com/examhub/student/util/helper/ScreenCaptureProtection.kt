package com.examhub.student.util.helper

import android.app.Activity
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Call protectScreenFromCapture() in any exam-taking screen to block screenshots/screen recording.
 *
 * To disable during local testing, comment the single call in the target Fragment, or temporarily
 * return early from enable() below.
 */
object ScreenCaptureProtection {
    private var activeRequests = 0

    fun enable(activity: Activity) {
        activeRequests += 1
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    fun disable(activity: Activity) {
        activeRequests = (activeRequests - 1).coerceAtLeast(0)
        if (activeRequests == 0) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

fun Fragment.protectScreenFromCapture() {
    var enabled = false
    viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (!enabled && activity != null) {
                ScreenCaptureProtection.enable(requireActivity())
                enabled = true
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            if (enabled && activity != null) {
                ScreenCaptureProtection.disable(requireActivity())
                enabled = false
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            onStop(owner)
        }
    })
}
