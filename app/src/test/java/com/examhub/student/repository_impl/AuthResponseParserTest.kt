package com.examhub.student.repository_impl

import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.util.helper.sanitizedStudentProfile
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.examhub.student.model.ApiException

class AuthResponseParserTest {
    private val gson = Gson()
    private val parser = AuthResponseParser(gson)

    @Test
    fun authTokenParsesNestedTokensAndUserFromDifferentEnvelopeLevels() {
        val root = gson.parse(
            """
            {
              "data": {
                "user": {
                  "id": "user-1",
                  "email": "student@example.test",
                  "full_name": "Nguyen Van A",
                  "role": "STUDENT",
                  "google_linked": true,
                  "has_password": true,
                  "auth_methods": {
                    "password": true,
                    "google": true
                  }
                },
                "result": {
                  "tokens": {
                    "access_token": "access",
                    "refresh_token": "refresh"
                  }
                }
              }
            }
            """
        )

        val result = parser.parseAuthToken(root)

        assertEquals("access", result.accessToken)
        assertEquals("refresh", result.refreshToken)
        assertEquals("user-1", result.user?.id)
        assertTrue(result.user?.googleLinked == true)
    }

    @Test
    fun inactiveMinimalUserIsStillParsedFromTokenResponse() {
        val root = gson.parse(
            """
            {
              "access_token": "access",
              "refresh_token": "refresh",
              "user": {
                "id": "user-1",
                "is_active": false
              }
            }
            """
        )

        val result = parser.parseAuthToken(root)

        assertEquals("user-1", result.user?.id)
        assertEquals(false, result.user?.isActive)
    }

    @Test
    fun successEnvelopeWithoutTokensReportsRegistrationRequirement() {
        val root = gson.parse(
            """
            {
              "data": {
                "registration_required": true,
                "message": "Create an account first"
              }
            }
            """
        )

        val error = runCatching { parser.parseAuthToken(root) }.exceptionOrNull() as ApiException

        assertEquals("GOOGLE_REGISTRATION_REQUIRED", error.code)
    }

    @Test
    fun successEnvelopeWithoutTokensReportsInactiveNestedUser() {
        val root = gson.parse(
            """
            {
              "data": {
                "user": {
                  "id": "user-1",
                  "is_active": false
                },
                "message": "Account inactive"
              }
            }
            """
        )

        val error = runCatching { parser.parseAuthToken(root) }.exceptionOrNull() as ApiException

        assertEquals("ACCOUNT_INACTIVE", error.code)
    }

    @Test
    fun successEnvelopeWithoutTokensReportsGoogleLinkRequirement() {
        val root = gson.parse(
            """
            {
              "data": {
                "google_link_required": true,
                "message": "Google is not linked"
              }
            }
            """
        )

        val error = runCatching { parser.parseAuthToken(root) }.exceptionOrNull() as ApiException

        assertEquals("GOOGLE_ACCOUNT_NOT_LINKED", error.code)
    }

    @Test
    fun successEnvelopeWithoutTokensReportsGoogleLinkRequirementFromBackendAliases() {
        val root = gson.parse(
            """
            {
              "data": {
                "requires_google_link": true,
                "message": "Existing account must link Google first"
              }
            }
            """
        )

        val error = runCatching { parser.parseAuthToken(root) }.exceptionOrNull() as ApiException

        assertEquals("GOOGLE_ACCOUNT_NOT_LINKED", error.code)
    }

    @Test
    fun registrationParsesStringFlagsAndNestedTokenObject() {
        val root = gson.parse(
            """
            {
              "message": "created",
              "data": {
                "requires_otp": "false",
                "auth": {
                  "accessToken": "access",
                  "refreshToken": "refresh"
                },
                "profile": {
                  "id": "user-1",
                  "email": "student@example.test",
                  "fullName": "Nguyen Van A",
                  "role": "STUDENT"
                }
              }
            }
            """
        )

        val result = parser.parseRegistration(root)

        assertFalse(result.requiresOtp)
        assertEquals("access", result.accessToken)
        assertEquals("refresh", result.refreshToken)
        assertEquals("created", result.message)
        assertEquals("user-1", result.user?.id)
    }

    @Test
    fun linkAndUnlinkUseOperationStateWhenBackendOnlyReturnsMessage() {
        val root = gson.parse("""{"data":{"message":"ok"}}""")

        assertTrue(parser.parseGoogleLink(root, expectedLinked = true).googleLinked)
        assertFalse(parser.parseGoogleLink(root, expectedLinked = false).googleLinked)
    }

    @Test
    fun linkParsesDocumentedAuthenticationState() {
        val root = gson.parse(
            """
            {
              "data": {
                "google_linked": true,
                "updated": false,
                "has_password": true,
                "auth_methods": {
                  "password": true,
                  "google": true
                }
              }
            }
            """
        )

        val result = parser.parseGoogleLink(root, expectedLinked = true)

        assertTrue(result.googleLinked)
        assertFalse(result.updated == true)
        assertTrue(result.hasPassword == true)
        assertTrue(result.authMethods?.password == true)
        assertTrue(result.authMethods?.google == true)
    }

    @Test
    fun linkParsesAuthenticationMethodsWrapperAndAliases() {
        val root = gson.parse(
            """
            {
              "response": {
                "authentication": {
                  "methods": {
                    "password_enabled": true,
                    "google_enabled": true
                  }
                }
              }
            }
            """
        )

        val result = parser.parseGoogleLink(root, expectedLinked = true)

        assertTrue(result.authMethods?.password == true)
        assertTrue(result.authMethods?.google == true)
    }

    @Test
    fun getMeParsesProfileWrapperAndNestedGoogleObject() {
        val root = gson.parse(
            """
            {
              "payload": {
                "profile": {
                  "id": "user-1",
                  "email": "student@example.test",
                  "name": "Nguyen Van A",
                  "role": "STUDENT",
                  "google_linked": true,
                  "has_password": false,
                  "auth_methods": {
                    "password": false,
                    "google": true
                  }
                }
              }
            }
            """
        )

        val user = parser.parseUser(root)

        assertTrue(user.googleLinked == true)
        assertFalse(user.hasPassword == true)
        assertTrue(user.authMethods?.google == true)
    }

    @Test
    fun explicitGoogleStateWinsOverAuthMethodsFallback() {
        val user = UserResponse(
            id = "user-1",
            email = "student@example.test",
            fullName = "Nguyen Van A",
            role = "STUDENT",
            googleLinked = false,
            authMethods = com.examhub.student.model.response.auth.AuthMethodsResponse(
                password = true,
                google = true
            )
        ).sanitizedStudentProfile()

        assertFalse(user.googleLinked == true)
        assertFalse(user.authMethods?.google == true)
    }

    @Test
    fun sanitizingPartialAuthenticationStateKeepsMethodsConsistent() {
        val user = UserResponse(
            id = "user-1",
            googleLinked = true,
            hasPassword = true,
            authMethods = null
        ).sanitizedStudentProfile()

        assertTrue(user.googleLinked == true)
        assertTrue(user.hasPassword == true)
        assertTrue(user.authMethods?.google == true)
        assertTrue(user.authMethods?.password == true)
    }

    private fun Gson.parse(raw: String) =
        fromJson(raw.trimIndent(), com.google.gson.JsonElement::class.java)
}
