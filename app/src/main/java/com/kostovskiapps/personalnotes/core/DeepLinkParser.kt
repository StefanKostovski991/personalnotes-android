package com.kostovskiapps.personalnotes.core

import com.kostovskiapps.personalnotes.BuildConfig
import com.kostovskiapps.personalnotes.data.codec.NotePayloadCodec

/**
 * Parses every way a note can arrive from another user of the app, so the UI layer only has
 * to react to one sealed type:
 *
 * - `personalnotes://note/d/<token>`            - the link carries the note itself
 * - `personalnotes://note/42`                   - a note already stored on this device
 * - `https://personalnotes.app/note/42`         - the same, as an app link
 * - a `content://` / `file://` uri of a `.pnote` file opened from a file manager
 */
object DeepLinkParser {

    sealed interface Incoming {
        /** Open a note that is already stored locally. */
        data class LocalNote(val id: Long) : Incoming

        /** A complete note travelled inside the link. */
        data class EmbeddedNote(
            val token: String,
            val inline: Boolean,
        ) : Incoming

        /** A `.pnote` document the user tapped (mail attachment, chat, file browser). */
        data class NoteDocument(val uriString: String, val mimeType: String?) : Incoming
    }

    fun parse(uriString: String?, mimeType: String? = null): Incoming? {
        if (uriString.isNullOrBlank()) return null
        val scheme = uriString.substringBefore(':').lowercase()

        // A tapped file, not a link.
        if (scheme == "content" || scheme == "file") {
            if (looksLikeNoteDocument(uriString, mimeType)) {
                return Incoming.NoteDocument(uriString, mimeType)
            }
            return null
        }

        val segments = LinkUtils.segments(uriString)
        if (segments.isEmpty()) return null

        val noteIndex = segments.indexOfFirst { it.equals("note", ignoreCase = true) }
        val tail = if (noteIndex >= 0) segments.drop(noteIndex + 1) else segments

        // personalnotes://note/d/<token>
        if (tail.firstOrNull().equals(NotePayloadCodec.LINK_SEGMENT, ignoreCase = true)) {
            val token = tail.getOrNull(1)
            return token?.takeIf { it.isNotBlank() }?.let { Incoming.EmbeddedNote(it, inline = true) }
        }

        val candidate = tail.firstOrNull() ?: segments.last()
        // personalnotes://note/42
        val id = candidate.toLongOrNull()
        if (id != null) {
            return Incoming.LocalNote(id)
        }
        // personalnotes://import?d=<token>
        LinkUtils.queryParameter(uriString, "d")?.let {
            return Incoming.EmbeddedNote(it, inline = false)
        }
        // A long opaque segment is treated as an embedded payload (share sheets sometimes
        // echo the url path only).
        return if (candidate.length > 40) Incoming.EmbeddedNote(candidate, inline = false) else null
    }

    private fun looksLikeNoteDocument(uriString: String, mimeType: String?): Boolean {
        if (mimeType != null) {
            if (mimeType.equals("application/vnd.personalnotes.note+json", ignoreCase = true)) return true
            if (mimeType.startsWith("audio/")) return false
        }
        val path = uriString.substringBefore('?').lowercase()
        return path.endsWith(".${BuildConfig.NOTE_FILE_EXTENSION}")
    }
}
