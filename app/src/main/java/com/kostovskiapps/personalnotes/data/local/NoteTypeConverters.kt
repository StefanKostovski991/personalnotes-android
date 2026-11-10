package com.kostovskiapps.personalnotes.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * File references are stored as a JSON array in a single TEXT column.
 *
 * A converter is used instead of `@Relation` because a note's attachments are an opaque,
 * ordered list of private file names - they are never queried on their own.
 */
class NoteTypeConverters {

    @TypeConverter
    fun fileRefsToString(refs: List<String>): String =
        JSON.encodeToString(ListSerializer(String.serializer()), refs)

    @TypeConverter
    fun stringToFileRefs(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            JSON.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
