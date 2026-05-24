package com.examhub.student.model.response.common

import java.io.Serializable

data class ApiEnvelope<T>(
    val data: T
) : Serializable
