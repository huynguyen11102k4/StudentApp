package com.examhub.student.repository_impl

import com.examhub.student.model.response.auth.AuthTokenResponse
import com.examhub.student.model.response.auth.AuthMethodsResponse
import com.examhub.student.model.response.auth.GoogleLinkResponse
import com.examhub.student.model.response.auth.StudentRegisterResponse
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.util.helper.sanitizedStudentProfile
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException

class AuthResponseParser(
    private val gson: Gson
) {
    fun parseAuthToken(root: JsonElement): AuthTokenResponse {
        val layers = root.envelopeObjects()
        val payload = layers.last()
        val tokens = layers.objectAt("tokens", "token", "auth") ?: payload
        val accessToken = tokens.stringAt("accessToken", "access_token", "token")
            ?: layers.stringAt("accessToken", "access_token")
            ?: throw authStateException(layers)
        val refreshToken = tokens.stringAt("refreshToken", "refresh_token")
            ?: layers.stringAt("refreshToken", "refresh_token")
            ?: throw JsonParseException("Missing refresh token")
        val user = parseOptionalUser(layers)
        return AuthTokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            mustChangePassword = layers.booleanAt("mustChangePassword", "must_change_password")
                ?: user?.mustChangePassword,
            user = user
        )
    }

    fun parseRegistration(root: JsonElement): StudentRegisterResponse {
        val layers = root.envelopeObjects()
        val tokens = layers.objectAt("tokens", "token", "auth")
        val user = parseOptionalUser(layers)
        return StudentRegisterResponse(
            message = layers.stringAt("message", "msg"),
            userId = layers.stringAt("userId", "user_id")
                ?: user?.id?.takeIf(String::isNotBlank),
            requiresOtp = layers.booleanAt("requiresOtp", "requires_otp", "otp_required") ?: false,
            accessToken = tokens?.stringAt("accessToken", "access_token", "token")
                ?: layers.stringAt("accessToken", "access_token"),
            refreshToken = tokens?.stringAt("refreshToken", "refresh_token")
                ?: layers.stringAt("refreshToken", "refresh_token"),
            mustChangePassword = layers.booleanAt("mustChangePassword", "must_change_password")
                ?: user?.mustChangePassword,
            user = user
        )
    }

    fun parseGoogleLink(root: JsonElement, expectedLinked: Boolean): GoogleLinkResponse {
        val layers = root.envelopeObjects()
        val user = parseOptionalUser(layers)
        val google = layers.objectAt("google", "googleAccount", "google_account")
        val authentication = layers.objectAt("authentication", "auth_state", "authState")
        val authMethodsObject = layers.objectAt("authMethods", "auth_methods", "loginMethods", "login_methods")
            ?: authentication?.objectAt("methods", "authMethods", "auth_methods")
            ?: user?.authMethods?.let(gson::toJsonTree)?.asJsonObject
        val authMethods = authMethodsObject?.let {
            gson.fromJson(it, AuthMethodsResponse::class.java)
        }
        val linked = layers.booleanAt(
            "googleLinked",
            "google_linked",
            "linked",
            "is_google_linked",
            "isGoogleLinked"
        ) ?: google?.booleanAt("linked", "googleLinked", "google_linked")
            ?: user?.googleLinked
            ?: authMethods?.google
            ?: expectedLinked
        return GoogleLinkResponse(
            googleLinked = linked,
            updated = layers.booleanAt("updated", "changed", "was_updated"),
            hasPassword = layers.booleanAt("hasPassword", "has_password")
                ?: user?.hasPassword
                ?: authMethods?.password,
            authMethods = authMethods?.copy(google = linked)
        )
    }

    fun parseUser(root: JsonElement): UserResponse {
        return parseOptionalUser(root.envelopeObjects())
            ?: throw JsonParseException("Missing user profile")
    }

    private fun parseOptionalUser(layers: List<JsonObject>): UserResponse? {
        val candidate = layers.asReversed().firstNotNullOfOrNull { layer ->
            layer.objectAt("user")
                ?: layer.objectAt("profile", "account")?.let { wrapper ->
                    wrapper.takeIf { it.looksLikeUser() } ?: wrapper.objectAt("user")
                }
                ?: layer.takeIf { it.looksLikeUser() }
        }
            ?: return null
        val merged = candidate.deepCopy()
        GOOGLE_STATE_KEYS.forEach { key ->
            if (!merged.has(key)) {
                layers.asReversed().firstNotNullOfOrNull { it.get(key) }
                    ?.let { merged.add(key, it) }
            }
        }
        val google = layers.objectAt("google", "googleAccount", "google_account")
            ?: candidate.objectAt("google", "googleAccount", "google_account")
        if (google != null && !merged.has("googleLinked")) {
            google.first("linked", "googleLinked", "google_linked")
                ?.let { merged.add("googleLinked", it) }
        }
        return gson.fromJson(merged, UserResponse::class.java)?.sanitizedStudentProfile()
    }

    private fun authStateException(layers: List<JsonObject>): Exception {
        val user = parseOptionalUser(layers)
        val error = layers.objectAt("error")
        val code = error?.stringAt("code", "errorCode", "error_code")
            ?: layers.stringAt("code", "errorCode", "error_code")
        val message = error?.stringAt("message", "msg")
            ?: layers.stringAt("message", "msg")
            ?: "Authentication failed"
        val isActive = layers.booleanAt("isActive", "is_active", "active") ?: user?.isActive
        val registrationRequired = layers.booleanAt(
            "registrationRequired",
            "registration_required",
            "requiresRegistration",
            "requires_registration"
        )
        val linkRequired = layers.booleanAt(
            "linkRequired",
            "link_required",
            "googleLinkRequired",
            "google_link_required"
        )
        return when {
            isActive == false -> com.examhub.student.model.ApiException("ACCOUNT_INACTIVE", message)
            registrationRequired == true ->
                com.examhub.student.model.ApiException("GOOGLE_REGISTRATION_REQUIRED", message)
            linkRequired == true || code?.contains("NOT_LINKED", ignoreCase = true) == true ->
                com.examhub.student.model.ApiException("GOOGLE_ACCOUNT_NOT_LINKED", message)
            !code.isNullOrBlank() -> com.examhub.student.model.ApiException(code, message)
            else -> JsonParseException("Missing access token")
        }
    }

    private fun JsonElement.envelopeObjects(): List<JsonObject> {
        var current = this
        val layers = mutableListOf<JsonObject>()
        repeat(5) {
            if (!current.isJsonObject) return@repeat
            val obj = current.asJsonObject
            layers += obj
            val nested = obj.first("data", "result", "payload", "response")
            if (nested == null || nested.isJsonNull || !nested.isJsonObject) return layers
            current = nested
        }
        if (layers.isEmpty()) throw JsonParseException("Expected object response")
        return layers
    }

    private fun JsonObject.looksLikeUser(): Boolean =
        has("id") || has("email") || has("fullName") || has("full_name") ||
            has("student") || has("role") || has("isActive") || has("is_active") ||
            has("googleLinked") || has("google_linked") ||
            has("authMethods") || has("auth_methods")

    private fun JsonObject.objectAt(vararg keys: String): JsonObject? =
        first(*keys)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.stringAt(vararg keys: String): String? =
        first(*keys)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)

    private fun JsonObject.booleanAt(vararg keys: String): Boolean? {
        val value = first(*keys)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
        return when {
            value.isBoolean -> value.asBoolean
            value.isNumber -> value.asInt != 0
            value.isString -> when (value.asString.trim().lowercase()) {
                "true", "1", "yes", "linked" -> true
                "false", "0", "no", "unlinked" -> false
                else -> null
            }
            else -> null
        }
    }

    private fun JsonObject.first(vararg keys: String): JsonElement? =
        keys.firstNotNullOfOrNull { key -> get(key)?.takeUnless(JsonElement::isJsonNull) }

    private fun List<JsonObject>.objectAt(vararg keys: String): JsonObject? =
        asReversed().firstNotNullOfOrNull { it.objectAt(*keys) }

    private fun List<JsonObject>.stringAt(vararg keys: String): String? =
        asReversed().firstNotNullOfOrNull { it.stringAt(*keys) }

    private fun List<JsonObject>.booleanAt(vararg keys: String): Boolean? =
        asReversed().firstNotNullOfOrNull { it.booleanAt(*keys) }

    private companion object {
        val GOOGLE_STATE_KEYS = listOf(
            "googleLinked",
            "google_linked",
            "linked",
            "is_google_linked",
            "isGoogleLinked",
            "hasPassword",
            "has_password",
            "authMethods",
            "auth_methods"
        )
    }
}
