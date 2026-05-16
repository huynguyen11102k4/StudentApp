package com.omr.scanner.student.model.response

import java.io.Serializable

data class PagedEnvelope<T>(
    val data: List<T>,
    val meta: PageMeta
) : Serializable
