package com.examhub.student.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.examhub.student.model.ApiResult
import com.examhub.student.repository.LockModeRepository
import kotlinx.coroutines.flow.first
import org.koin.java.KoinJavaComponent.get

class ViolationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository: LockModeRepository = get(LockModeRepository::class.java)
        return when (val result = repository.flushQueuedViolations().first { it !is ApiResult.Loading }) {
            is ApiResult.Success -> Result.success()
            is ApiResult.Error -> Result.retry()
            else -> Result.retry()
        }
    }
}
