package com.omr.scanner.student

import android.app.Application
import com.omr.scanner.student.di.NetworkModule
import com.omr.scanner.student.di.RepositoryModule
import com.omr.scanner.student.di.ViewModelModule
import com.omr.scanner.student.service.ThemePreferenceManager
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
