package com.examhub.student.util.helper

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UploadUrlResolver {
    fun resolveUploadUrl(rawUrl: String?, apiBaseUrl: String): String? {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isBlank()) return null

        val apiBase = apiBaseUrl.toHttpUrlOrNull() ?: return trimmed
        val origin = apiBase.origin()
        val absolute = trimmed.toHttpUrlOrNull()
        if (absolute != null) {
            val uploadPath = normalizedUploadPath(absolute.encodedPath) ?: return trimmed
            return if (absolute.host.isLocalBackendHost()) {
                origin + uploadPath + absolute.querySuffix()
            } else {
                trimmed
            }
        }

        val path = normalizedUploadPath(trimmed) ?: normalizeRelativePath(trimmed)
        return origin + path
    }

    private fun normalizedUploadPath(path: String): String? {
        val normalized = if (path.startsWith("/")) path else "/$path"
        val uploadIndex = normalized.indexOf("/uploads")
        if (uploadIndex < 0) return null

        val uploadPath = normalized.substring(uploadIndex)
        return if (normalized.startsWith("/api/v1/uploads")) {
            normalized
        } else {
            "/api/v1$uploadPath"
        }
    }

    private fun normalizeRelativePath(path: String): String {
        val normalized = if (path.startsWith("/")) path else "/$path"
        return if (normalized.startsWith("/api/v1/")) normalized else "/api/v1$normalized"
    }

    private fun HttpUrl.origin(): String {
        val defaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
        val portPart = if (defaultPort) "" else ":$port"
        return "$scheme://$host$portPart"
    }

    private fun HttpUrl.querySuffix(): String =
        query?.let { "?$it" }.orEmpty()

    private fun String.isLocalBackendHost(): Boolean {
        val normalized = lowercase()
        if (normalized == "localhost" || normalized == "::1" || normalized.startsWith("127.")) return true
        if (normalized.startsWith("192.168.")) return true
        if (normalized.startsWith("10.")) return true
        val secondOctet = normalized.split(".").getOrNull(1)?.toIntOrNull()
        return normalized.startsWith("172.") && secondOctet in 16..31
    }
}
