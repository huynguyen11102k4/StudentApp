package com.omr.scanner.student.model.response

import java.io.Serializable

data class PageMeta(
    val page: Int,
    val limit: Int,
    val total: Int
) : Serializable
