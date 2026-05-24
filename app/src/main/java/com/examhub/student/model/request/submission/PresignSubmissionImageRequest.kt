package com.examhub.student.model.request.submission

import com.google.gson.annotations.SerializedName

data class PresignSubmissionImageRequest(
    @SerializedName("file_size")
    val fileSize: Long,
    @SerializedName("file_type")
    val fileType: String,
    @SerializedName("image_kind")
    val imageKind: String? = null
)
