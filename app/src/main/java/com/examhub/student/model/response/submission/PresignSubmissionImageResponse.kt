package com.examhub.student.model.response.submission

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class PresignSubmissionImageResponse(
    @SerializedName("upload_url")
    val uploadUrl: String,
    @SerializedName("file_url")
    val fileUrl: String,
    @SerializedName("file_path")
    val filePath: String,
    @SerializedName("expires_in")
    val expiresIn: Int
)
