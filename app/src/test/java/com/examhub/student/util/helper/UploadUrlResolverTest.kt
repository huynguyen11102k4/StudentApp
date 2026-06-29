package com.examhub.student.util.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadUrlResolverTest {
    private val apiBaseUrl = "http://192.168.80.37:3001/api/v1/"

    @Test
    fun relativeApiUploadUsesCurrentBackendOrigin() {
        assertEquals(
            "http://192.168.80.37:3001/api/v1/uploads/avatar.jpg",
            UploadUrlResolver.resolveUploadUrl("/api/v1/uploads/avatar.jpg", apiBaseUrl)
        )
    }

    @Test
    fun relativeLegacyUploadPathIsMovedUnderApiUploadPath() {
        assertEquals(
            "http://192.168.80.37:3001/api/v1/uploads/avatar.jpg",
            UploadUrlResolver.resolveUploadUrl("/uploads/avatar.jpg", apiBaseUrl)
        )
    }

    @Test
    fun legacyLocalhostUploadUrlUsesCurrentBackendOrigin() {
        assertEquals(
            "http://192.168.80.37:3001/api/v1/uploads/avatar.jpg",
            UploadUrlResolver.resolveUploadUrl("http://localhost:3001/uploads/avatar.jpg", apiBaseUrl)
        )
    }

    @Test
    fun legacyWifiUploadUrlUsesCurrentBackendOriginAndKeepsQuery() {
        assertEquals(
            "http://192.168.80.37:3001/api/v1/uploads/avatar.jpg?v=1",
            UploadUrlResolver.resolveUploadUrl("http://192.168.1.12:3001/api/v1/uploads/avatar.jpg?v=1", apiBaseUrl)
        )
    }

    @Test
    fun externalUploadUrlIsPreserved() {
        val external = "https://cdn.example.test/uploads/avatar.jpg"

        assertEquals(external, UploadUrlResolver.resolveUploadUrl(external, apiBaseUrl))
    }
}
