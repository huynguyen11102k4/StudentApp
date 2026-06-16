package com.examhub.student.util.helper

import com.examhub.student.R
import com.examhub.student.model.ApiException
import java.text.Normalizer

enum class GoogleLoginFailure {
    ACTIVATE_ACCOUNT,
    REGISTER_ACCOUNT,
    ACCOUNT_NOT_LINKED,
    SHOW_ERROR
}

object AuthErrorMapper {
    fun googleLoginFailure(error: ApiException): GoogleLoginFailure {
        val code = error.normalizedCode()
        val message = error.searchableMessage()
        return when {
            code.contains("INACTIVE") ||
                message.contains("chua kich hoat") ||
                message.contains("xac thuc otp") -> GoogleLoginFailure.ACTIVATE_ACCOUNT

            code in NOT_LINKED_CODES ||
                code.contains("NOT_LINKED") ||
                message.contains("chua lien ket") -> GoogleLoginFailure.ACCOUNT_NOT_LINKED

            error.httpCode == 404 ||
                code in REGISTRATION_CODES ||
                code.contains("NOT_REGISTERED") ||
                code.contains("USER_NOT_FOUND") ||
                code.contains("ACCOUNT_NOT_FOUND") ||
                code.contains("STUDENT_NOT_FOUND") ||
                message.contains("chua dang ky") -> GoogleLoginFailure.REGISTER_ACCOUNT

            else -> GoogleLoginFailure.SHOW_ERROR
        }
    }

    fun message(error: ApiException, resources: ResourceProvider, fallbackRes: Int): String {
        val resource = when (error.normalizedCode()) {
            "INVALID_GOOGLE_TOKEN", "GOOGLE_TOKEN_INVALID", "GOOGLE_TOKEN_EXPIRED" ->
                R.string.auth_error_invalid_google_token
            "GOOGLE_EMAIL_MISMATCH", "EMAIL_MISMATCH" ->
                R.string.auth_error_google_email_mismatch
            "GOOGLE_ACCOUNT_ALREADY_LINKED", "GOOGLE_ALREADY_LINKED" ->
                R.string.auth_error_google_already_linked
            "GOOGLE_ACCOUNT_MISMATCH", "WRONG_GOOGLE_ACCOUNT" ->
                R.string.auth_error_google_account_mismatch
            "GOOGLE_ACCOUNT_NOT_LINKED", "GOOGLE_NOT_LINKED", "ACCOUNT_NOT_LINKED", "LINK_REQUIRED" ->
                R.string.auth_error_google_not_linked
            "ACCOUNT_INACTIVE", "USER_INACTIVE", "STUDENT_INACTIVE" ->
                R.string.auth_error_account_inactive
            "INVALID_CREDENTIALS", "INVALID_PASSWORD", "UNAUTHORIZED" ->
                R.string.auth_error_invalid_credentials
            "PASSWORD_REQUIRED_BEFORE_UNLINK", "PASSWORD_REQUIRED" ->
                R.string.profile_google_unlink_password_required
            "NETWORK_ERROR", "TIMEOUT" ->
                R.string.auth_error_network
            else -> fallbackRes
        }
        return resources.getString(resource)
    }

    private fun ApiException.normalizedCode(): String = code.trim()
        .uppercase()
        .replace('-', '_')
        .replace(' ', '_')

    private fun ApiException.searchableMessage(): String {
        return Normalizer.normalize(message.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace('đ', 'd')
    }

    private val NOT_LINKED_CODES = setOf(
        "GOOGLE_ACCOUNT_NOT_LINKED",
        "GOOGLE_NOT_LINKED",
        "ACCOUNT_NOT_LINKED",
        "LINK_REQUIRED"
    )

    private val REGISTRATION_CODES = setOf(
        "GOOGLE_REGISTRATION_REQUIRED",
        "REGISTRATION_REQUIRED",
        "REGISTER_REQUIRED",
        "ACCOUNT_REQUIRED",
        "EMAIL_NOT_REGISTERED"
    )
}
