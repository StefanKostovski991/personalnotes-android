package com.kostovskiapps.personalnotes.data.repository

import android.content.Context
import androidx.core.content.FileProvider
import com.kostovskiapps.personalnotes.BuildConfig
import com.kostovskiapps.personalnotes.data.codec.NotePayloadCodec
import com.kostovskiapps.personalnotes.data.codec.toPayload
import com.kostovskiapps.personalnotes.data.share.AttachmentStore
import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.domain.repository.ExportedNote
import com.kostovskiapps.personalnotes.domain.repository.NoteShareRepository
import com.kostovskiapps.personalnotes.domain.repository.SharedAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes a note into `cache/exports` as a `.pnote` file that the share sheet can hand to
 * WhatsApp, mail, Drive, a nearby-device transfer, ... and builds the matching deep link.
 *
 * The file lives in the cache so the system can clean it up; nothing the user owns is
 * written outside the app without them choosing a destination.
 */
class AndroidNoteShareRepository(
    private val context: Context,
    private val attachments: AttachmentStore,
    private val maxInlineAttachmentBytes: Long = OfflineNoteRepository.DEFAULT_MAX_INLINE_ATTACHMENT,
) : NoteShareRepository {

    override suspend fun exportNote(note: Note): ExportedNote = withContext(Dispatchers.IO) {
        val payload = note.toPayload(attachments = inlineAttachments(note))
        val bytes = NotePayloadCodec.encodeToFileBytes(payload)

        val directory = exportDir()
        val file = File(directory, buildFileName(note)).apply { writeBytes(bytes) }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        ExportedNote(
            fileUriString = uri.toString(),
            fileName = file.name,
            mimeType = MIME_TYPE,
            sizeBytes = bytes.size.toLong(),
            shareLink = buildShareLink(note),
        )
    }

    override fun buildShareLink(note: Note): String {
        // The link carries the note itself, so the receiver does not need the file.
        val token = NotePayloadCodec.encodeToLinkToken(note.toPayload())
        return "${BuildConfig.NOTE_LINK_SCHEME}://note/${NotePayloadCodec.LINK_SEGMENT}/$token"
    }

    override suspend fun resolveAttachment(fileRef: String): SharedAttachment? =
        withContext(Dispatchers.IO) {
            attachments.toShared(fileRef, mimeTypeOf(fileRef))
        }

    private suspend fun inlineAttachments(note: Note) = note.fileRefs.mapNotNull { ref ->
        val size = attachments.size(ref)
        if (size <= 0L || size > maxInlineAttachmentBytes) return@mapNotNull null
        val bytes = attachments.readBytes(ref) ?: return@mapNotNull null
        com.kostovskiapps.personalnotes.data.codec.NoteAttachmentPayload(
            name = ref,
            mimeType = mimeTypeOf(ref),
            base64Data = NotePayloadCodec.base64UrlEncode(bytes),
        )
    }

    private fun exportDir(): File = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }

    private fun buildFileName(note: Note): String {
        val slug = note.title
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(32)
            .ifBlank { "note" }
        return "$slug-${note.id}.${BuildConfig.NOTE_FILE_EXTENSION}"
    }

    private fun mimeTypeOf(ref: String): String = when (ref.substringAfterLast('.', "")) {
        "m4a", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }

    companion object {
        private const val EXPORT_DIR = "exports"
        const val MIME_TYPE = "application/vnd.personalnotes.note+json"
    }
}
