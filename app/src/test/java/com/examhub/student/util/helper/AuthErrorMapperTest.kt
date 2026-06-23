package com.examhub.student.util.helper

import com.examhub.student.model.ApiException
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMapperTest {
    @Test
    fun existingAccountWithoutGoogleLinkDoesNotNavigateToRegistration() {
        assertEquals(
            GoogleLoginFailure.ACCOUNT_NOT_LINKED,
            AuthErrorMapper.googleLoginFailure(
                ApiException("GOOGLE_ACCOUNT_NOT_LINKED", "Google is not linked", 409)
            )
        )
    }

    @Test
    fun linkGoogleRequiredDoesNotNavigateToRegistration() {
        assertEquals(
            GoogleLoginFailure.ACCOUNT_NOT_LINKED,
            AuthErrorMapper.googleLoginFailure(
                ApiException("LINK_GOOGLE_ACCOUNT", "Existing account must link Google", 409)
            )
        )
    }

    @Test
    fun missingAccountNavigatesToRegistration() {
        assertEquals(
            GoogleLoginFailure.REGISTER_ACCOUNT,
            AuthErrorMapper.googleLoginFailure(
                ApiException("STUDENT_NOT_FOUND", "Student not found", 404)
            )
        )
    }

    @Test
    fun inactiveAccountNavigatesToActivation() {
        assertEquals(
            GoogleLoginFailure.ACTIVATE_ACCOUNT,
            AuthErrorMapper.googleLoginFailure(
                ApiException("ACCOUNT_INACTIVE", "Account inactive", 403)
            )
        )
    }
}
