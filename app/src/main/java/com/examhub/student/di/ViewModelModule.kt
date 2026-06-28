package com.examhub.student.di

import com.examhub.student.ui.appeals.AppealDetailViewModel
import com.examhub.student.ui.appeals.AppealsListViewModel
import com.examhub.student.ui.cameraar.CameraARViewModel
import com.examhub.student.ui.classdetail.ClassDetailViewModel
import com.examhub.student.ui.classlist.ClassListViewModel
import com.examhub.student.ui.dashboard.DashboardViewModel
import com.examhub.student.ui.examdetail.ExamDetailViewModel
import com.examhub.student.ui.examlist.ExamListViewModel
import com.examhub.student.ui.examstart.ExamStartViewModel
import com.examhub.student.ui.forgotpassword.ForgotPasswordViewModel
import com.examhub.student.ui.login.LoginViewModel
import com.examhub.student.ui.lockmode.LockModeViewModel
import com.examhub.student.ui.notifications.NotificationsViewModel
import com.examhub.student.ui.profile.ProfileViewModel
import com.examhub.student.ui.register.RegisterViewModel
import com.examhub.student.ui.results.ResultDetailViewModel
import com.examhub.student.ui.results.ResultsListViewModel
import com.examhub.student.ui.settings.SettingsViewModel
import com.examhub.student.ui.smartreview.SmartReviewViewModel
import com.examhub.student.ui.splash.SplashViewModel
import com.examhub.student.ui.submissionend.SubmissionEndViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

object ViewModelModule {
    val module = module {
        // Splash
        viewModel { SplashViewModel(get()) }

        // Auth
        viewModel { LoginViewModel(get(), get(), get()) }
        viewModel { RegisterViewModel(get(), get(), get()) }
        viewModel { ForgotPasswordViewModel(get(), get()) }
        viewModel { ProfileViewModel(get(), get()) }

        // Dashboard
        viewModel { DashboardViewModel(get(), get(), get(), get(), get(), get()) }

        // Exams
        viewModel { ExamDetailViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        viewModel { ExamListViewModel(get(), get(), get()) }
        viewModel { ExamStartViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
        viewModel { LockModeViewModel(get(), get(), get(), get(), get(), get(), get()) }
        viewModel { ResultsListViewModel(get()) }
        viewModel { ResultDetailViewModel(get(), get(), get(), get()) }
        viewModel { SubmissionEndViewModel(get(), get(), get()) }

        // Classes
        viewModel { ClassListViewModel(get(), get(), get(), get()) }
        viewModel { ClassDetailViewModel(get(), get(), get(), get(), get()) }

        // OMR confirm/review
        viewModel { SmartReviewViewModel(get(), get(), get(), get(), get()) }

        // Notifications
        viewModel { NotificationsViewModel(get(), get(), get()) }

        // Appeals
        viewModel { AppealsListViewModel(get()) }
        viewModel { AppealDetailViewModel(get(), get()) }

        // Others (no repository dependencies)
        viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { CameraARViewModel(get(), get(), get(), get(), get(), get()) }
    }
}
