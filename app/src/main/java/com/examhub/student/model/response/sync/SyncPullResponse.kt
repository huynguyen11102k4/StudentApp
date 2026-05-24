package com.examhub.student.model.response.sync

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class SyncPullResponse(
    @SerializedName("exams")
    val exams: List<SyncExamItem>? = null,

    @SerializedName("templates")
    val templates: List<SyncTemplateItem>? = null,

    @SerializedName("exam_versions")
    val examVersions: List<SyncExamVersionItem>? = null
)
