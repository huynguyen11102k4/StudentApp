package com.examhub.student.model.response.auth

import com.google.gson.annotations.SerializedName

data class GoogleLinkResponse(
    @SerializedName(value = "googleLinked", alternate = ["google_linked", "linked", "is_google_linked", "isGoogleLinked"])
    val googleLinked: Boolean,
    @SerializedName(value = "updated", alternate = ["changed"])
    val updated: Boolean? = null,
    @SerializedName(value = "hasPassword", alternate = ["has_password"])
    val hasPassword: Boolean? = null,
    @SerializedName(value = "authMethods", alternate = ["auth_methods"])
    val authMethods: AuthMethodsResponse? = null
)
