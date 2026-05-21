package com.examhub.student.di

import com.examhub.student.repository.AppealsRepository
import com.examhub.student.repository.AuthRepository
import com.examhub.student.repository.ClassRepository
import com.examhub.student.repository.ExamRepository
import com.examhub.student.repository.LockModeRepository
import com.examhub.student.repository.NotificationRepository
import com.examhub.student.repository.ResultsRepository
import com.examhub.student.repository.StudentSubmissionRepository
import com.examhub.student.repository_impl.AppealsRepositoryImpl
import com.examhub.student.repository_impl.AuthRepositoryImpl
import com.examhub.student.repository_impl.ClassRepositoryImpl
import com.examhub.student.repository_impl.ExamRepositoryImpl
import com.examhub.student.repository_impl.LockModeRepositoryImpl
import com.examhub.student.repository_impl.NotificationRepositoryImpl
import com.examhub.student.repository_impl.ResultsRepositoryImpl
import com.examhub.student.repository_impl.StudentSubmissionRepositoryImpl
import com.examhub.student.omr.OmrProcessor
import com.examhub.student.omr.OmrReviewStore
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
