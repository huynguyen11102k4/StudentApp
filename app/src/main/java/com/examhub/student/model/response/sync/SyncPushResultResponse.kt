package com.examhub.student.model.response.sync

import com.google.gson.annotations.SerializedName

data class SyncPushResultResponse(
    @SerializedName("client_job_id")
    val clientJobId: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("record_id")
    val recordId: String? = null
)
