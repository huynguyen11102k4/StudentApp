package com.examhub.student.service

import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

class AuthApiServiceContractTest {
    @Test
    fun profileAndGoogleEndpointsMatchStudentAuthContract() {
        assertEquals(
            "student/auth/profile",
            AuthApiService::class.java.getMethod("getMe", kotlin.coroutines.Continuation::class.java)
                .getAnnotation(GET::class.java)!!
                .value
        )
        assertEquals(
            "student/auth/profile/google-link",
            AuthApiService::class.java.methods.single { it.name == "linkGoogle" }
                .getAnnotation(POST::class.java)!!
                .value
        )
        assertEquals(
            "student/auth/profile/google-link",
            AuthApiService::class.java.getMethod("unlinkGoogle", kotlin.coroutines.Continuation::class.java)
                .getAnnotation(DELETE::class.java)!!
                .value
        )
        assertEquals(
            "student/auth/profile/avatar",
            AuthApiService::class.java.methods.single { it.name == "uploadAvatar" }
                .getAnnotation(POST::class.java)!!
                .value
        )
    }
}
