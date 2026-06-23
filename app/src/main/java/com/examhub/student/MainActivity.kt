package com.examhub.student

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.examhub.student.databinding.ActivityMainBinding
import com.examhub.student.kiosk.KioskModeController
import com.examhub.student.kiosk.KioskModeState
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.lock.LockViolationRequest
import com.examhub.student.repository.LockModeRepository
import com.examhub.student.service.AuthEvent
import com.examhub.student.service.FcmTokenRegistrar
import com.examhub.student.service.TokenManager
import com.examhub.student.util.helper.NotificationNavigationHelper
import com.examhub.student.util.helper.PushNotificationHandoff
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val fcmTokenRegistrar: FcmTokenRegistrar by inject()
    private val tokenManager: TokenManager by inject()
    private val lockModeRepository: LockModeRepository by inject()
    private lateinit var kioskModeController: KioskModeController
    private var activeLockSessionId: String = ""
    private var activeLockScreen: String = ""
    private var lockModeStopped = false
    private val pushNotificationHandoff = PushNotificationHandoff(
        splashDestinationId = R.id.splashFragment,
        authDestinationIds = setOf(
            R.id.loginFragment,
            R.id.registerFragment,
            R.id.forgotPasswordFragment
        )
    )
    private var repinJob: Job? = null
    private var screenDimJob: Job? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        kioskModeController = KioskModeController(this)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        setupBottomNavigation(navController)
        requestNotificationPermissionIfNeeded()
        fcmTokenRegistrar.syncCurrentToken(lifecycleScope)
        flushQueuedViolations()
        observeAuthEvents()
        handlePushIntent(intent)
    }

    fun enterKioskMode(): KioskModeState {
        return kioskModeController.enter()
    }

    fun exitKioskMode() {
        clearActiveLockSession()
        clearLockFlowScreenPolicy()
        kioskModeController.exit()
    }

    override fun onStart() {
        super.onStart()
        lockModeStopped = false
        flushQueuedViolations()
    }

    override fun onResume() {
        super.onResume()
        scheduleLockFlowRepin(LOCK_REPIN_DELAY_MS)
        applyLockFlowScreenPolicy()
    }

    override fun onStop() {
        if (!isChangingConfigurations && !isFinishing) {
            logLockModeBackgroundViolation()
        }
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) scheduleLockFlowRepin(LOCK_REPIN_DELAY_MS)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (activeLockSessionId.isNotBlank()) {
            restoreLockFlowBrightness()
            scheduleScreenDim()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushIntent(intent)
    }

    private fun setupBottomNavigation(navController: NavController) {
        runCatching {
            binding.bottomNavigation.javaClass
                .getMethod("setItemActiveIndicatorEnabled", Boolean::class.javaPrimitiveType)
                .invoke(binding.bottomNavigation, false)
        }
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_home -> R.id.dashboardFragment
                R.id.nav_exams -> R.id.examListFragment
                R.id.nav_classes -> R.id.classListFragment
                R.id.nav_notifications -> R.id.notificationsFragment
                R.id.nav_profile -> R.id.settingsFragment
                else -> return@setOnItemSelectedListener false
            }

            if (navController.currentDestination?.id == target) {
                return@setOnItemSelectedListener true
            }

            val args = if (target == R.id.examListFragment) {
                bundleOf(
                    "gradingType" to "",
                    "title" to getString(R.string.exam_list_default_title)
                )
            } else {
                null
            }

            if (target == R.id.dashboardFragment && navController.popBackStack(R.id.dashboardFragment, false)) {
                return@setOnItemSelectedListener true
            }

            navController.navigate(
                target,
                args,
                navOptions {
                    launchSingleTop = true
                    popUpTo(R.id.dashboardFragment) {
                        inclusive = false
                        saveState = false
                    }
                }
            )
            true
        }

        navController.addOnDestinationChangedListener { _, destination, arguments ->
            val selectedItemId = when (destination.id) {
                R.id.dashboardFragment -> R.id.nav_home
                R.id.examListFragment -> R.id.nav_exams
                R.id.classListFragment -> R.id.nav_classes
                R.id.notificationsFragment -> R.id.nav_notifications
                R.id.settingsFragment -> R.id.nav_profile
                else -> null
            }

            binding.bottomNavigation.visibility = if (selectedItemId == null) View.GONE else View.VISIBLE
            if (selectedItemId != null && binding.bottomNavigation.selectedItemId != selectedItemId) {
                binding.bottomNavigation.selectedItemId = selectedItemId
            }

            updateActiveLockSession(destination.id, arguments)
            if (destination.id.isLockFlowDestination()) {
                applyLockFlowScreenPolicy()
                scheduleLockFlowRepin(0L)
            } else {
                clearLockFlowScreenPolicy()
            }
            tryRoutePendingNotification()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(permission)
        }
    }

    private fun handlePushIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_NOTIFICATION) return
        if (pushNotificationHandoff.capture(intent?.extras)) {
            intent?.replaceExtras(Bundle())
            tryRoutePendingNotification()
        }
    }

    private fun tryRoutePendingNotification() {
        when (val action = pushNotificationHandoff.nextAction(
            navController.currentDestination?.id,
            tokenManager.hasToken()
        )) {
            PushNotificationHandoff.Action.None -> Unit
            PushNotificationHandoff.Action.NavigateLogin -> navigateToLogin()
            is PushNotificationHandoff.Action.NavigateDestination -> navigateNotificationDestination(action.destination)
        }
    }

    private fun navigateNotificationDestination(destination: NotificationNavigationHelper.Destination) {
        when (destination) {
            is NotificationNavigationHelper.Destination.AppealDetail -> {
                navController.navigate(
                    R.id.appealDetailFragment,
                    bundleOf("appealId" to destination.appealId),
                    navOptions { launchSingleTop = true }
                )
            }
            is NotificationNavigationHelper.Destination.ResultDetail -> {
                navController.navigate(
                    R.id.resultDetailFragment,
                    bundleOf("sheetId" to destination.sheetId),
                    navOptions { launchSingleTop = true }
                )
            }
            is NotificationNavigationHelper.Destination.ExamDetail -> {
                navController.navigate(
                    R.id.examDetailFragment,
                    bundleOf("examId" to destination.examId),
                    navOptions { launchSingleTop = true }
                )
            }
            NotificationNavigationHelper.Destination.AppealsList -> {
                navController.navigate(R.id.appealsListFragment, null, navOptions { launchSingleTop = true })
            }
            NotificationNavigationHelper.Destination.ResultsList -> {
                navController.navigate(R.id.resultsListFragment, null, navOptions { launchSingleTop = true })
            }
            NotificationNavigationHelper.Destination.ExamList -> {
                navController.navigate(R.id.examListFragment, null, navOptions { launchSingleTop = true })
            }
            NotificationNavigationHelper.Destination.Notifications -> {
                navController.navigate(R.id.notificationsFragment, null, navOptions { launchSingleTop = true })
            }
            NotificationNavigationHelper.Destination.None -> Unit
        }
    }

    private fun observeAuthEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                tokenManager.authEvents.collect { event ->
                    if (event is AuthEvent.SessionExpired) {
                        navigateToLogin()
                    }
                }
            }
        }
    }

    private fun flushQueuedViolations() {
        lifecycleScope.launch {
            lockModeRepository.flushQueuedViolations().collect { result ->
                if (result is ApiResult.Success && result.data > 0) {
                    // Queue flushed silently; students should not need to manage this state.
                }
            }
        }
    }

    private fun updateActiveLockSession(destinationId: Int, arguments: Bundle?) {
        when (destinationId) {
            R.id.lockModeFragment,
            R.id.cameraARFragment,
            R.id.smartReviewFragment,
            R.id.manualCropFragment -> {
                val sessionId = arguments?.getString("sessionId").orEmpty()
                if (sessionId.isNotBlank()) activeLockSessionId = sessionId
                activeLockScreen = resources.getResourceEntryName(destinationId)
            }
            R.id.submissionEndFragment -> clearActiveLockSession()
            else -> {
                if (activeLockSessionId.isNotBlank()) clearActiveLockSession()
            }
        }
    }

    private fun clearActiveLockSession() {
        activeLockSessionId = ""
        activeLockScreen = ""
        lockModeStopped = false
        repinJob?.cancel()
        repinJob = null
    }

    private fun Int.isLockFlowDestination(): Boolean {
        return this == R.id.lockModeFragment ||
            this == R.id.cameraARFragment ||
            this == R.id.smartReviewFragment ||
            this == R.id.manualCropFragment
    }

    private fun scheduleLockFlowRepin(delayMillis: Long) {
        if (activeLockSessionId.isBlank()) return
        if (navController.currentDestination?.id?.isLockFlowDestination() != true) return
        if (kioskModeController.currentLockTaskMode() != ActivityManager.LOCK_TASK_MODE_NONE) return

        repinJob?.cancel()
        repinJob = lifecycleScope.launch {
            delay(delayMillis)
            if (
                activeLockSessionId.isNotBlank() &&
                navController.currentDestination?.id?.isLockFlowDestination() == true &&
                kioskModeController.currentLockTaskMode() == ActivityManager.LOCK_TASK_MODE_NONE
            ) {
                enterKioskMode()
            }
        }
    }

    private fun applyLockFlowScreenPolicy() {
        if (activeLockSessionId.isBlank()) return
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        restoreLockFlowBrightness()
        scheduleScreenDim()
    }

    private fun scheduleScreenDim() {
        if (activeLockSessionId.isBlank()) return
        screenDimJob?.cancel()
        screenDimJob = lifecycleScope.launch {
            delay(LOCK_SCREEN_DIM_DELAY_MS)
            if (activeLockSessionId.isNotBlank()) {
                val attrs = window.attributes
                attrs.screenBrightness = LOCK_DIM_BRIGHTNESS
                window.attributes = attrs
            }
        }
    }

    private fun restoreLockFlowBrightness() {
        val attrs = window.attributes
        if (attrs.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = attrs
        }
    }

    private fun clearLockFlowScreenPolicy() {
        screenDimJob?.cancel()
        screenDimJob = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        restoreLockFlowBrightness()
    }

    private fun logLockModeBackgroundViolation() {
        val sessionId = activeLockSessionId.takeIf { it.isNotBlank() } ?: return
        if (lockModeStopped) return
        lockModeStopped = true

        lockModeRepository.queueViolation(
            LockViolationRequest(
                sessionId = sessionId,
                violationType = "switch_app",
                occurredAt = nowIso(),
                evidenceData = mapOf(
                    "screen" to activeLockScreen.ifBlank { "unknown" },
                    "source" to "activity_on_stop",
                    "reason" to "app_background",
                    "violation_label" to getString(R.string.lock_violation_background_label),
                    "teacher_message" to getString(R.string.lock_violation_background_teacher_message)
                )
            )
        )
        flushQueuedViolations()
    }

    private fun nowIso(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }

    private fun navigateToLogin() {
        val currentId = navController.currentDestination?.id
        if (currentId == R.id.loginFragment || currentId == R.id.splashFragment) return
        navController.navigate(
            R.id.loginFragment,
            null,
            navOptions {
                launchSingleTop = true
                popUpTo(R.id.nav_graph) {
                    inclusive = true
                }
            }
        )
    }

    companion object {
        const val ACTION_OPEN_NOTIFICATION = "com.examhub.student.OPEN_NOTIFICATION"
        private const val LOCK_REPIN_DELAY_MS = 3_000L
        private const val LOCK_SCREEN_DIM_DELAY_MS = 60_000L
        private const val LOCK_DIM_BRIGHTNESS = 0.08f
    }
}
