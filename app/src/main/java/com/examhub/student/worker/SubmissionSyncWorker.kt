package com.examhub.student.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.examhub.student.service.OfflineSubmissionManager
import com.examhub.student.data.local.model.WorkerSyncResult
import org.koin.java.KoinJavaComponent.get

class SubmissionSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            val manager: OfflineSubmissionManager = get(OfflineSubmissionManager::class.java)
            val id = inputData.getString(KEY_CLIENT_SUBMISSION_ID)
            when (manager.syncPending(id)) {
                WorkerSyncResult.SUCCESS -> Result.success()
                WorkerSyncResult.RETRY -> Result.retry()
                WorkerSyncResult.FAILURE -> Result.failure()
            }
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val KEY_CLIENT_SUBMISSION_ID = "client_submission_id"
    }
}
