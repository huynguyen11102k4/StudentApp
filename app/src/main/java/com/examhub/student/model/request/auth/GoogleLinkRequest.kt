package com.examhub.student.model.request.auth

import com.google.gson.annotations.SerializedName

data class GoogleLinkRequest(
    @SerializedName("google_id_token")
    val googleIdToken: String
)
