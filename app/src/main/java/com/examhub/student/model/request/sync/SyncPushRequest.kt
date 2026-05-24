package com.examhub.student.model.request.sync

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class SyncPushRequest(
    @SerializedName("items")
    val items: List<SyncPushItemRequest>
)
