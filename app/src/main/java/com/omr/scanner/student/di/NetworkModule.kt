package com.omr.scanner.student.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.omr.scanner.student.BuildConfig
import com.omr.scanner.student.service.AuthInterceptor
import com.omr.scanner.student.service.AuthApiService
import com.omr.scanner.student.service.ClassApiService
import com.omr.scanner.student.service.ETagCacheInterceptor
import com.omr.scanner.student.service.ETagCacheManager
import com.omr.scanner.student.service.FcmTokenRegistrar
import com.omr.scanner.student.service.ExamApiService
import com.omr.scanner.student.service.NotificationApiService
import com.omr.scanner.student.service.NotificationPreferenceManager
import com.omr.scanner.student.service.AppealsApiService
import com.omr.scanner.student.service.LockModeApiService
import com.omr.scanner.student.service.RefreshTokenService
import com.omr.scanner.student.service.ResultsApiService
import com.omr.scanner.student.service.StudentSubmissionApiService
import com.omr.scanner.student.service.TokenAuthenticator
import com.omr.scanner.student.service.TokenManager
import com.omr.scanner.student.service.UnauthorizedInterceptor
import com.omr.scanner.student.service.OfflineCacheManager
import com.omr.scanner.student.service.ViolationQueueManager
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
        single { TokenManager(androidContext()) }
        single { NotificationPreferenceManager(androidContext()) }
        single { FcmTokenRegistrar(get(), get(), get()) }
        single { ETagCacheManager(androidContext()) }
        single { OfflineCacheManager(androidContext()) }
        single { ViolationQueueManager(androidContext(), get()) }
        single { AuthInterceptor(get()) }
        single { UnauthorizedInterceptor(get()) }
        single { ETagCacheInterceptor(get()) }

        single {
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        }

        single(named("refreshOkHttp")) {
            val tokenManager: TokenManager = get()
            val deviceInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-Device-Id", tokenManager.getDeviceId())
                    .build()
                chain.proceed(request)
            }

            OkHttpClient.Builder()
                .addInterceptor(deviceInterceptor)
                .addInterceptor(get<HttpLoggingInterceptor>())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
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
    }
}
