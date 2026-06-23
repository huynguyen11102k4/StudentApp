package com.examhub.student.util.helper

import java.util.Locale

object NotificationTextResolver {
    data class Payload(
        val type: String = "",
        val notificationTitle: String? = null,
        val notificationBody: String? = null,
        val titleLocKey: String? = null,
        val titleLocArgs: List<String>? = null,
        val bodyLocKey: String? = null,
        val bodyLocArgs: List<String>? = null,
        val data: Map<String, String> = emptyMap()
    )

    data class Text(
        val title: String,
        val body: String
    )

    interface Strings {
        fun resolve(key: String, args: List<String> = emptyList()): String?
        fun defaultTitle(type: String): String
        fun defaultBody(type: String): String
    }

    fun resolve(payload: Payload, strings: Strings): Text {
        val title = payload.titleLocKey
            ?.takeIf(String::isNotBlank)
            ?.let { strings.resolve(it.normalizedKey(), payload.titleLocArgs.orEmpty()) }
            ?: payload.notificationTitle
            ?: payload.data.firstText("title")
            ?: strings.defaultTitle(payload.type)

        val body = payload.bodyLocKey
            ?.takeIf(String::isNotBlank)
            ?.let { strings.resolve(it.normalizedKey(), payload.bodyLocArgs.orEmpty()) }
            ?: payload.notificationBody
            ?: payload.data.firstText("body", "content", "message")
            ?: payload.data["exam_name"]?.let { "${strings.defaultTitle(payload.type)}: $it" }
            ?: strings.defaultBody(payload.type)

        return Text(title, body)
    }

    fun parseLocArgs(raw: String?): List<String>? {
        val args = raw ?: return null
        if (args.isBlank()) return emptyList()
        return runCatching {
            val jsonArray = org.json.JSONArray(args)
            List(jsonArray.length()) { index -> jsonArray.getString(index) }
        }.getOrElse {
            args.split(",")
                .map(String::trim)
                .filter(String::isNotBlank)
        }
    }

    fun normalizeType(type: String): String =
        type.trim().lowercase(Locale.ROOT).replace('-', '_')

    private fun String.normalizedKey(): String =
        trim().replace('-', '_')

    private fun Map<String, String>.firstText(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> get(key)?.takeIf(String::isNotBlank) }
}
