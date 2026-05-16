package com.omr.scanner.student.model.response

import java.io.Serializable

data class ApiEnvelope<T>(
    val data: T
) : Serializable
