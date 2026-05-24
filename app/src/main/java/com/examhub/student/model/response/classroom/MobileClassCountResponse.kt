package com.examhub.student.model.response.classroom

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MobileClassCountResponse(
    val students: Int? = null,
    val exams: Int? = null,
    @SerializedName("student_classes")
    val studentClasses: Int? = null,
    @SerializedName("studentClasses")
    val studentClassesCamel: Int? = null
)
