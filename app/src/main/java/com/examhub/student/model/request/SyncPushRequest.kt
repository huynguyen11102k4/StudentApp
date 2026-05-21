package com.examhub.student.model.request

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class SyncPushRequest(
    @SerializedName("items")
    val items: List<SyncPushItemRequest>
)

data class SyncPushItemRequest(
    @SerializedName("client_job_id")
    val clientJobId: String,

    @SerializedName("table_name")
    val tableName: String,

    @SerializedName("operation")
    val operation: String,

    @SerializedName("data")
    val data: JsonObject
)
