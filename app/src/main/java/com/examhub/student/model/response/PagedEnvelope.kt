package com.examhub.student.model.response

import java.io.Serializable

data class PagedEnvelope<T>(
    val data: List<T> = emptyList(),
    val meta: PageMeta? = null
) : Serializable
