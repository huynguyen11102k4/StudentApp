package com.examhub.student.ui.submissionend

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.model.ApiResult
import com.examhub.student.repository.ResultsRepository
import com.examhub.student.service.OfflineSubmissionManager
import com.examhub.student.data.local.model.SubmissionSyncStatus
import com.examhub.student.data.local.submission.QueuedSubmissionEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SubmissionEndViewModel(
    private val resultsRepository: ResultsRepository,
    private val context: Context,
    private val offlineSubmissionManager: OfflineSubmissionManager
) : ViewModel() {
    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _navigation = MutableSharedFlow<ResultNavigation>(extraBufferCapacity = 1)
    val navigation: SharedFlow<ResultNavigation> = _navigation.asSharedFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()
    private val _submissionState = MutableStateFlow(SubmissionEndUiState())
    val submissionState: StateFlow<SubmissionEndUiState> = _submissionState.asStateFlow()

    fun observeSubmission(clientSubmissionId: String) {
        if (clientSubmissionId.isBlank()) return
        offlineSubmissionManager.scheduleSync(clientSubmissionId)
        viewModelScope.launch {
            offlineSubmissionManager.observe(clientSubmissionId).collect { item ->
                _submissionState.value = item.toUiState()
            }
        }
    }

    fun openResult(sheetId: String, examId: String) {
        if (_isResolving.value) return
        val persistedResultId = _submissionState.value.resultId
        val resolvedArgumentId = persistedResultId.ifBlank { sheetId }
        if (resolvedArgumentId.isNotBlank()) {
            _navigation.tryEmit(ResultNavigation.Detail(resolvedArgumentId))
            return
        }
        if (_submissionState.value.isTerminalFailure) {
            _message.tryEmit(context.getString(_submissionState.value.statusRes ?: R.string.submission_status_failed))
            return
        }
        if (examId.isBlank()) {
            _message.tryEmit(context.getString(R.string.result_detail_pending_message))
            return
        }

        viewModelScope.launch {
            _isResolving.value = true
            val response = resultsRepository.getResults(page = "1", limit = "100")
                .first { it !is ApiResult.Loading }
            _isResolving.value = false

            when (response) {
                is ApiResult.Success -> {
                    val result = response.data.data
                        .filter { it.exam?.id == examId }
                        .maxByOrNull { it.createdAt.orEmpty() }
                    val resolvedSheetId = result?.id.orEmpty()
                    if (resolvedSheetId.isNotBlank()) {
                        _navigation.tryEmit(
                            ResultNavigation.Detail(
                                sheetId = resolvedSheetId,
                                examStatus = result?.exam?.status.orEmpty()
                            )
                        )
                    } else {
                        _submissionState.value = _submissionState.value.copy(
                            statusRes = R.string.submission_status_waiting_grading,
                            isWaitingForGrading = true
                        )
                        _message.tryEmit(context.getString(R.string.result_detail_pending_message))
                    }
                }
                is ApiResult.Error -> {
                    _message.tryEmit(context.getString(R.string.result_detail_pending_message))
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    sealed interface ResultNavigation {
        data class Detail(val sheetId: String, val examStatus: String = "") : ResultNavigation
    }

    private fun QueuedSubmissionEntity?.toUiState(): SubmissionEndUiState {
        if (this == null) return SubmissionEndUiState(statusRes = R.string.submission_status_saved)
        return when (status) {
            SubmissionSyncStatus.PENDING_SYNC.name -> SubmissionEndUiState(
                statusRes = R.string.submission_status_waiting_network
            )
            SubmissionSyncStatus.UPLOADING_IMAGES.name -> SubmissionEndUiState(
                statusRes = R.string.submission_status_uploading
            )
            SubmissionSyncStatus.SYNCING.name -> SubmissionEndUiState(
                statusRes = R.string.submission_status_syncing
            )
            SubmissionSyncStatus.SYNCED.name -> {
                val resolvedResultId = resultId.orEmpty()
                SubmissionEndUiState(
                    statusRes = if (resolvedResultId.isBlank()) {
                        R.string.submission_status_waiting_grading
                    } else {
                        R.string.submission_status_synced
                    },
                    resultId = resolvedResultId,
                    isWaitingForGrading = resolvedResultId.isBlank()
                )
            }
            SubmissionSyncStatus.FAILED_CAPTURE_AFTER_DEADLINE.name -> SubmissionEndUiState(
                statusRes = R.string.submission_status_after_deadline,
                isTerminalFailure = true
            )
            SubmissionSyncStatus.FAILED_TERMINAL.name -> SubmissionEndUiState(
                statusRes = terminalErrorResource(lastErrorCode),
                isTerminalFailure = true
            )
            else -> SubmissionEndUiState(statusRes = R.string.submission_status_saved)
        }
    }

    private fun terminalErrorResource(code: String?): Int = when (code?.uppercase()) {
        "INVALID_OFFLINE_PERMIT" -> R.string.submission_status_invalid_permit
        "OFFLINE_PERMIT_MISMATCH" -> R.string.submission_status_permit_mismatch
        "SUBMISSION_DEVICE_MISMATCH" -> R.string.submission_status_device_mismatch
        "CLIENT_SUBMISSION_ID_REQUIRED" -> R.string.submission_status_missing_client_id
        "CLIENT_SUBMISSION_ID_CONFLICT" -> R.string.submission_status_client_id_conflict
        "LOCAL_PAYLOAD_INVALID" -> R.string.submission_status_invalid_local_data
        "LOCAL_IMAGE_INVALID" -> R.string.submission_status_invalid_local_image
        else -> R.string.submission_status_failed
    }
}

data class SubmissionEndUiState(
    val statusRes: Int? = null,
    val resultId: String = "",
    val isWaitingForGrading: Boolean = false,
    val isTerminalFailure: Boolean = false
)
