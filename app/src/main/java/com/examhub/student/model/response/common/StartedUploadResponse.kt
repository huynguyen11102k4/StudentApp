package com.examhub.student.model.response.common

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class StartedUploadResponse(
    @SerializedName("presign_endpoint")
    val presignEndpoint: String,
    @SerializedName("submit_endpoint")
    val submitEndpoint: String,
    @SerializedName("supported_image_types")
    val supportedImageTypes: List<String>,
    @SerializedName("max_file_size_bytes")
    val maxFileSizeBytes: Long
)
