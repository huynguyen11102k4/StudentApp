package com.omr.scanner.student.di

import com.omr.scanner.student.repository.AppealsRepository
import com.omr.scanner.student.repository.AuthRepository
import com.omr.scanner.student.repository.ClassRepository
import com.omr.scanner.student.repository.ExamRepository
import com.omr.scanner.student.repository.LockModeRepository
import com.omr.scanner.student.repository.NotificationRepository
import com.omr.scanner.student.repository.ResultsRepository
import com.omr.scanner.student.repository.StudentSubmissionRepository
import com.omr.scanner.student.repository_impl.AppealsRepositoryImpl
import com.omr.scanner.student.repository_impl.AuthRepositoryImpl
import com.omr.scanner.student.repository_impl.ClassRepositoryImpl
import com.omr.scanner.student.repository_impl.ExamRepositoryImpl
import com.omr.scanner.student.repository_impl.LockModeRepositoryImpl
import com.omr.scanner.student.repository_impl.NotificationRepositoryImpl
import com.omr.scanner.student.repository_impl.ResultsRepositoryImpl
import com.omr.scanner.student.repository_impl.StudentSubmissionRepositoryImpl
import com.omr.scanner.student.omr.OmrProcessor
import com.omr.scanner.student.omr.OmrReviewStore
import org.koin.dsl.module

object RepositoryModule {
    val module = module {
        single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
        single<ExamRepository> { ExamRepositoryImpl(get(), get()) }
        single<ClassRepository> { ClassRepositoryImpl(get(), get()) }
        single<NotificationRepository> { NotificationRepositoryImpl(get(), get()) }
        single<AppealsRepository> { AppealsRepositoryImpl(get(), get()) }
        single<StudentSubmissionRepository> { StudentSubmissionRepositoryImpl(get(), get(), get()) }
        single<LockModeRepository> { LockModeRepositoryImpl(get(), get(), get()) }
        single<ResultsRepository> { ResultsRepositoryImpl(get(), get()) }
        single { OmrProcessor(get(), get()) }
        single { OmrReviewStore() }
    }
}
