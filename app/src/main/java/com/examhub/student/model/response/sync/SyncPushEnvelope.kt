package com.examhub.student.model.response.sync

import com.google.gson.annotations.SerializedName

data class SyncPushEnvelope(
    @SerializedName("results")
    val results: List<SyncPushResultResponse>
)
