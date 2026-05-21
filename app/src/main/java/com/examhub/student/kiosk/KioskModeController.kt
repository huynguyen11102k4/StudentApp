package com.examhub.student.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.content.getSystemService

class KioskModeController(private val activity: Activity) {
    private val devicePolicyManager: DevicePolicyManager? = activity.getSystemService()
    private val adminComponent = ComponentName(activity, StudentDeviceAdminReceiver::class.java)

    val isDeviceOwner: Boolean
        get() = devicePolicyManager?.isDeviceOwnerApp(activity.packageName) == true

    fun enter(): KioskModeState {
        enableImmersive(activity.window)
        if (isDeviceOwner) {
            runCatching {
                devicePolicyManager?.setLockTaskPackages(adminComponent, arrayOf(activity.packageName))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    devicePolicyManager?.setLockTaskFeatures(adminComponent, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
                }
            }
        }

        return runCatching {
            activity.startLockTask()
            if (isDeviceOwner) KioskModeState.DeviceOwnerLocked else KioskModeState.ScreenPinned
        }.getOrElse {
            KioskModeState.Unavailable(it.message.orEmpty())
        }
    }

    fun exit() {
        runCatching { activity.stopLockTask() }
        disableImmersive(activity.window)
    }

    fun currentLockTaskMode(): Int {
        return activity.getSystemService<ActivityManager>()?.lockTaskModeState
            ?: ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun enableImmersive(window: Window) {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun disableImmersive(window: Window) {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}

sealed class KioskModeState {
    data object DeviceOwnerLocked : KioskModeState()
    data object ScreenPinned : KioskModeState()
    data class Unavailable(val reason: String) : KioskModeState()
}
