package com.kostovskiapps.personalnotes.data.codec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire format used when a note travels to another user of the app.
 *
 * It is versioned on purpose: [schema] identifies the payload kind and [version] lets a
 * future release migrate old files instead of failing to open them.
 *
 * NOTE: every key is spelled out explicitly and matches the Kotlin property name. Decoding a
 * file whose keys do not match silently drops the field (unknown keys are ignored), so the key
 * names are pinned by [NotePayloadCodecTest].
 */
@Serializable
data class NotePayload(
    @SerialName("schema") val schema: String = SCHEMA,
    @SerialName("version") val version: Int = VERSION,
    @SerialName("id") val id: String,
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("createdAt") val createdAt: Long = 0L,
    @SerialName("updatedAt") val updatedAt: Long = 0L,
    @SerialName("colorId") val colorId: String? = null,
    @SerialName("kindId") val kindId: String? = null,
    @SerialName("location") val location: NoteLocationPayload? = null,
    @SerialName("attachments") val attachments: List<NoteAttachmentPayload> = emptyList(),
) {
    companion object {
        const val SCHEMA = "com.kostovskiapps.personalnotes/note"
        const val VERSION = 1
        const val MEME_TYPE_NOTE = "application/vnd.personalnotes.note+json"
    }
}

@Serializable
data class NoteLocationPayload(
    @SerialName("label") val label: String = "",
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
)

@Serializable
data class NoteAttachmentPayload(
    @SerialName("name") val name: String = "",
    @SerialName("mimeType") val mimeType: String = "application/octet-stream",
    @SerialName("data") val base64Data: String? = null,
)
