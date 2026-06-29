package com.examhub.student.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.examhub.student.BuildConfig
import com.examhub.student.service.AuthInterceptor
import com.examhub.student.service.AuthApiService
import com.examhub.student.service.BackendUrlInterceptor
import com.examhub.student.service.BackendUrlManager
import com.examhub.student.service.ClassApiService
import com.examhub.student.service.ETagCacheInterceptor
import com.examhub.student.service.ETagCacheManager
import com.examhub.student.service.FcmTokenRegistrar
import com.examhub.student.service.ExamApiService
import com.examhub.student.service.NotificationApiService
import com.examhub.student.service.NotificationPreferenceManager
import com.examhub.student.service.AppealsApiService
import com.examhub.student.service.LockModeApiService
import com.examhub.student.service.RefreshTokenService
import com.examhub.student.service.ResultsApiService
import com.examhub.student.service.StudentSubmissionApiService
import com.examhub.student.service.TokenAuthenticator
import com.examhub.student.service.TokenManager
import com.examhub.student.service.UnauthorizedInterceptor
import com.examhub.student.service.OfflineCacheManager
import com.examhub.student.service.ActiveExamSessionStore
import com.examhub.student.service.ViolationQueueManager
import com.examhub.student.security.EncryptedSubmissionFileStore
import com.examhub.student.security.KeystoreCrypto
import com.examhub.student.security.SecurePermitStore
import com.examhub.student.data.local.StudentAppDatabase
import com.examhub.student.data.local.StudentDatabaseMigrations
import com.examhub.student.service.OfflineSubmissionManager
import androidx.room.Room
import com.examhub.student.util.helper.ResourceProvider
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    val module = module {
        single { GsonBuilder().create() }
        single { ResourceProvider(androidContext()) }
        single { KeystoreCrypto() }
        single {
            Room.databaseBuilder(
                androidContext(),
                StudentAppDatabase::class.java,
                "student_app.db"
            )
                .addMigrations(StudentDatabaseMigrations.MIGRATION_1_2)
                .build()
        }
        single { get<StudentAppDatabase>().studentCacheDao() }
        single { get<StudentAppDatabase>().queuedSubmissionDao() }
        single { TokenManager(androidContext(), get(), get()) }
        single { SecurePermitStore(androidContext(), get(), get()) }
        single { EncryptedSubmissionFileStore(androidContext(), get()) }
        single { NotificationPreferenceManager(androidContext()) }
        single { FcmTokenRegistrar(get(), get(), get(), androidContext()) }
        single { ETagCacheManager(androidContext(), get()) }
        single { OfflineCacheManager(androidContext(), get(), get()) }
        single { BackendUrlManager(androidContext(), get(), get(), get(), get()) }
        single { ActiveExamSessionStore(androidContext(), get(), get(), get()) }
        single { ViolationQueueManager(androidContext(), get(), get(), get()) }
        single { AuthInterceptor(get()) }
        single { BackendUrlInterceptor(get()) }
        single { UnauthorizedInterceptor(get()) }
        single { ETagCacheInterceptor(get()) }
        single(named("deviceIdInterceptor")) {
            val tokenManager: TokenManager = get()
            Interceptor { chain ->
                val original = chain.request()
                val request = if (original.header("X-Device-Id").isNullOrBlank()) {
                    original.newBuilder()
                        .header("X-Device-Id", tokenManager.getDeviceId())
                        .build()
                } else {
                    original
                }
                chain.proceed(request)
            }
        }

        single {
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        }

        single(named("refreshOkHttp")) {
            OkHttpClient.Builder()
                .addInterceptor(get<BackendUrlInterceptor>())
                .addInterceptor(get<Interceptor>(named("deviceIdInterceptor")))
                .addInterceptor(get<HttpLoggingInterceptor>())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        single(named("storageUploadOkHttp")) {
            OkHttpClient.Builder()
                .addInterceptor(get<BackendUrlInterceptor>())
                .addInterceptor(get<HttpLoggingInterceptor>())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
        }

        single(named("refreshRetrofit")) {
            Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(get(named("refreshOkHttp")))
                .addConverterFactory(GsonConverterFactory.create(get<Gson>()))
                .build()
        }

        single<RefreshTokenService> {
            get<Retrofit>(named("refreshRetrofit")).create(RefreshTokenService::class.java)
        }

        single { TokenAuthenticator(get(), get()) }

        single {
            OkHttpClient.Builder()
                .addInterceptor(get<BackendUrlInterceptor>())
                .addInterceptor(get<Interceptor>(named("deviceIdInterceptor")))
                .addInterceptor(get<AuthInterceptor>())
                .addInterceptor(get<ETagCacheInterceptor>())
                .authenticator(get<TokenAuthenticator>())
                .addInterceptor(get<UnauthorizedInterceptor>())
                .addInterceptor(get<HttpLoggingInterceptor>())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        single {
            Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(get())
                .addConverterFactory(GsonConverterFactory.create(get<Gson>()))
                .build()
        }

        single<AuthApiService> { get<Retrofit>().create(AuthApiService::class.java) }
        single<ExamApiService> { get<Retrofit>().create(ExamApiService::class.java) }
        single<ClassApiService> { get<Retrofit>().create(ClassApiService::class.java) }
        single<NotificationApiService> { get<Retrofit>().create(NotificationApiService::class.java) }
        single<AppealsApiService> { get<Retrofit>().create(AppealsApiService::class.java) }
        single<StudentSubmissionApiService> { get<Retrofit>().create(StudentSubmissionApiService::class.java) }
        single<LockModeApiService> { get<Retrofit>().create(LockModeApiService::class.java) }
        single<ResultsApiService> { get<Retrofit>().create(ResultsApiService::class.java) }
        single {
            OfflineSubmissionManager(
                androidContext(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get()
            )
        }
    }
}
