package com.kostovskiapps.personalnotes.core

import java.util.Locale

/** Lightweight URL helpers shared by the deep link layer. */
object LinkUtils {

    /**
     * Splits an intent-provided string into normalised url segments.
     *
     * Custom-scheme links are not always parsed the way a browser would parse http links, so
     * the scheme (with its optional `//`) is removed and the remainder split on `/`.
     */
    fun segments(url: String): List<String> {
        val trimmed = url.trim()
        val withoutScheme = run {
            val index = trimmed.indexOf("://")
            when {
                index >= 0 -> trimmed.substring(index + 3)
                else -> trimmed.substringAfter(':', trimmed).trimStart('/')
            }
        }
        return withoutScheme
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore('!')
            .split('/')
            .map(String::trim)
            .filter(String::isNotEmpty)
    }

    fun queryParameter(url: String, key: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isBlank()) return null
        return query.split('&').firstNotNullOfOrNull { part ->
            val name = part.substringBefore('=')
            if (name.equals(key, ignoreCase = true)) part.substringAfter('=', "") else null
        }
    }

    fun slug(value: String, maxLength: Int = 32): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(maxLength)
        .ifBlank { "note" }
}
