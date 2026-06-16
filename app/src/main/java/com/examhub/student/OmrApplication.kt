package com.examhub.student

import android.app.Application
import android.content.Context
import com.examhub.student.di.NetworkModule
import com.examhub.student.di.RepositoryModule
import com.examhub.student.di.ViewModelModule
import com.examhub.student.service.LanguagePreferenceManager
import com.examhub.student.service.OmrFirebaseMessagingService
import com.examhub.student.service.ThemePreferenceManager
import com.examhub.student.service.ViolationQueueManager
import com.examhub.student.service.OfflineSubmissionManager
import org.opencv.android.OpenCVLoader
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext

class OmrApplication : Application() {
    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
        OpenCVLoader.initLocal()
        LanguagePreferenceManager.applySavedLanguage(this)
        ThemePreferenceManager.applySavedMode(this)
        OmrFirebaseMessagingService.ensureNotificationChannel(this)
        startKoin {
            androidContext(this@OmrApplication)
            modules(
                NetworkModule.module,
                RepositoryModule.module,
                ViewModelModule.module
            )
        }
        GlobalContext.get().get<ViolationQueueManager>().takeIf { it.count() > 0 }?.scheduleSync()
        GlobalContext.get().get<OfflineSubmissionManager>().schedulePendingSync()
    }
}
