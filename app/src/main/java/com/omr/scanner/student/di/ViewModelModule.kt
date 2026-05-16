package com.omr.scanner.student.di

import com.omr.scanner.student.ui.appeals.AppealDetailViewModel
import com.omr.scanner.student.ui.appeals.AppealsListViewModel
import com.omr.scanner.student.ui.cameraar.CameraARViewModel
import com.omr.scanner.student.ui.classdetail.ClassDetailViewModel
import com.omr.scanner.student.ui.classlist.ClassListViewModel
import com.omr.scanner.student.ui.dashboard.DashboardViewModel
import com.omr.scanner.student.ui.examdetail.ExamDetailViewModel
import com.omr.scanner.student.ui.examlist.ExamListViewModel
import com.omr.scanner.student.ui.examstart.ExamStartViewModel
import com.omr.scanner.student.ui.forgotpassword.ForgotPasswordViewModel
import com.omr.scanner.student.ui.login.LoginViewModel
import com.omr.scanner.student.ui.lockmode.LockModeViewModel
import com.omr.scanner.student.ui.notifications.NotificationsViewModel
import com.omr.scanner.student.ui.profile.ProfileViewModel
import com.omr.scanner.student.ui.register.RegisterViewModel
import com.omr.scanner.student.ui.results.ResultDetailViewModel
import com.omr.scanner.student.ui.results.ResultsListViewModel
import com.omr.scanner.student.ui.settings.SettingsViewModel
import com.omr.scanner.student.ui.smartreview.SmartReviewViewModel
import com.omr.scanner.student.ui.splash.SplashViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

object ViewModelModule {
    val module = module {
        // Splash
        viewModel { SplashViewModel(get()) }

        // Auth
        viewModel { LoginViewModel(get(), get()) }
        viewModel { RegisterViewModel(get()) }
        viewModel { ForgotPasswordViewModel(get()) }
        viewModel { ProfileViewModel(get()) }

        // Dashboard
        viewModel { DashboardViewModel(get(), get(), get(), get()) }

        // Exams
        viewModel { ExamDetailViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { ExamListViewModel(get(), get()) }
        viewModel { ExamStartViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { LockModeViewModel(get(), get()) }
        viewModel { ResultsListViewModel(get(), get()) }
        viewModel { ResultDetailViewModel(get(), get(), get()) }

        // Classes
        viewModel { ClassListViewModel(get(), get()) }
        viewModel { ClassDetailViewModel(get()) }

        // OMR confirm/review
        viewModel { SmartReviewViewModel(get(), get()) }

        // Notifications
        viewModel { NotificationsViewModel(get(), get()) }

        // Appeals
        viewModel { AppealsListViewModel(get()) }
        viewModel { AppealDetailViewModel(get()) }

        // Others (no repository dependencies)
        viewModel { SettingsViewModel(get(), get(), get(), get()) }
        viewModel { CameraARViewModel(get(), get(), get()) }
    }
}
