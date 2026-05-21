package com.examhub.student

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
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
import com.examhub.student.model.request.LockViolationRequest
import com.examhub.student.repository.LockModeRepository
import com.examhub.student.service.AuthEvent
import com.examhub.student.service.FcmTokenRegistrar
import com.examhub.student.service.TokenManager
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
        kioskModeController.exit()
    }

    override fun onStart() {
        super.onStart()
        lockModeStopped = false
        flushQueuedViolations()
    }

    override fun onStop() {
        if (!isChangingConfigurations && !isFinishing) {
            logLockModeBackgroundViolation()
        }
        super.onStop()
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

            navController.navigate(
                target,
                args,
                navOptions {
                    launchSingleTop = true
                    popUpTo(R.id.dashboardFragment) {
                        inclusive = false
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
        if (!tokenManager.hasToken()) return
        val extras = intent?.extras ?: return
        val route = extras.getString("route").orEmpty()
        val type = extras.getString("type").orEmpty()
        val appealId = extras.getString("appeal_id")
            ?: extras.getString("appealId")
            ?: extras.getString("target_id")
            ?: extras.getString("entity_id")
            ?: extras.getString("link")?.extractLastId()

        if ((route == "appeal_detail" || type.equals("appeal_created", ignoreCase = true) || type.equals("appeal_updated", ignoreCase = true)) && !appealId.isNullOrBlank()) {
            navController.navigate(
                R.id.appealDetailFragment,
                bundleOf("appealId" to appealId),
                navOptions { launchSingleTop = true }
            )
            intent.replaceExtras(Bundle())
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
                    "source" to "activity_on_stop"
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

    private fun String.extractLastId(): String? {
        return split('/', '?', '#')
            .asReversed()
            .firstOrNull { it.length >= 16 && it.any(Char::isDigit) }
    }
}
