package com.examhub.student

import android.app.Application
import com.examhub.student.di.NetworkModule
import com.examhub.student.di.RepositoryModule
import com.examhub.student.di.ViewModelModule
import com.examhub.student.service.ThemePreferenceManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OmrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePreferenceManager.applySavedMode(this)
        startKoin {
            androidContext(this@OmrApplication)
            modules(
                NetworkModule.module,
                RepositoryModule.module,
                ViewModelModule.module
            )
        }
    }
}
