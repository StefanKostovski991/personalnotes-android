package com.kostovskiapps.personalnotes.data.repository

import com.kostovskiapps.personalnotes.data.codec.NotePayload
import com.kostovskiapps.personalnotes.data.codec.NotePayloadCodec
import com.kostovskiapps.personalnotes.data.codec.NoteAttachmentPayload
import com.kostovskiapps.personalnotes.data.codec.toDomain
import com.kostovskiapps.personalnotes.data.codec.toEntity
import com.kostovskiapps.personalnotes.data.codec.toPayload
import com.kostovskiapps.personalnotes.data.local.NoteDao
import com.kostovskiapps.personalnotes.data.local.NoteEntity
import com.kostovskiapps.personalnotes.data.share.AttachmentStore
import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.domain.model.NoteImportResult
import com.kostovskiapps.personalnotes.domain.model.NoteImportSource
import com.kostovskiapps.personalnotes.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Room + private files implementation of [NoteRepository].
 *
 * Importing is idempotent: the incoming payload is fingerprinted, and a note with the same
 * fingerprint is reported as [NoteImportResult.AlreadyExists] instead of being duplicated.
 */
class OfflineNoteRepository(
    private val dao: NoteDao,
    private val attachments: AttachmentStore,
    private val maxInlineAttachmentBytes: Long = DEFAULT_MAX_INLINE_ATTACHMENT,
) : NoteRepository {

    override fun observeNotes(): Flow<List<Note>> =
        dao.observeAll().map { entities -> entities.map(NoteEntity::toDomain) }

    override fun observeNote(id: Long): Flow<Note?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getNote(id: Long): Note? = withContext(Dispatchers.IO) {
        dao.findById(id)?.toDomain()
    }

    override suspend fun saveNote(note: Note): Long = withContext(Dispatchers.IO) {
        // A recording written to the cache becomes permanent only once the note is saved.
        val fileRefs = note.fileRefs.map { ref ->
            if (ref.startsWith(CACHE_PREFIX)) {
                val cached = attachments.resolve(ref.removePrefix(CACHE_PREFIX))
                if (cached != null && cached.exists()) attachments.persist(cached) else ref
            } else {
                ref
            }
        }
        val normalized = note.copy(fileRefs = fileRefs)
        val payload = normalized.toPayload(attachments = buildAttachments(normalized))
        val entity = normalized.toEntity(NotePayloadCodec.fingerprint(payload))

        val existingId = dao.findByFingerprint(entity.contentFingerprint)?.id
        when {
            // The editor always edits the copy it loaded, so an id means "update".
            normalized.isPersisted -> {
                dao.update(entity)
                normalized.id
            }

            existingId != null -> existingId

            else -> dao.insert(entity)
        }
    }

    override suspend fun deleteNote(id: Long) = withContext(Dispatchers.IO) {
        val entity = dao.findById(id) ?: return@withContext
        attachments.deleteAll(entity.fileRefs)
        dao.deleteById(id)
    }

    override suspend fun importNote(
        source: InputStream,
        sourceKind: NoteImportSource,
    ): NoteImportResult = withContext(Dispatchers.IO) {
        val payload = try {
            NotePayloadCodec.parse(source.readBytes())
        } catch (error: Exception) {
            return@withContext NoteImportResult.Failed(
                error.message ?: "This note could not be read.",
            )
        }

        val fingerprint = NotePayloadCodec.fingerprint(payload)
        dao.findByFingerprint(fingerprint)?.let { existing ->
            return@withContext NoteImportResult.AlreadyExists(existing.toDomain())
        }

        val fileRefs = payload.attachments.mapNotNull { attachment ->
            val data = attachment.base64Data ?: return@mapNotNull null
            runCatching {
                attachments.write(attachment.name, NotePayloadCodec.base64UrlDecode(data))
            }.getOrNull()
        }

        val note = payload.toDomain(fileRefs = fileRefs)
        val id = dao.insert(note.toEntity(fingerprint))
        NoteImportResult.Imported(note.copy(id = id))
    }

    override suspend fun decodeNote(source: InputStream): Note = withContext(Dispatchers.IO) {
        NotePayloadCodec.parse(source.readBytes()).toDomain(fileRefs = emptyList())
    }

    /** Inlines small attachments so a shared note stays self-contained. */
    private suspend fun buildAttachments(note: Note): List<NoteAttachmentPayload> =
        note.fileRefs.mapNotNull { ref ->
            val size = attachments.size(ref)
            if (size <= 0L || size > maxInlineAttachmentBytes) return@mapNotNull null
            val bytes = attachments.readBytes(ref) ?: return@mapNotNull null
            NoteAttachmentPayload(
                name = ref,
                mimeType = mimeTypeFor(ref),
                base64Data = NotePayloadCodec.base64UrlEncode(bytes),
            )
        }

    private fun mimeTypeFor(ref: String): String = when (ref.substringAfterLast('.', "")) {
        "m4a", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

    companion object {
        /** Marker used to stage a not-yet-persisted recording. */
        const val CACHE_PREFIX = "cache:"

        /** ~2 MB keeps a spoken note inside a single shareable file or link. */
        const val DEFAULT_MAX_INLINE_ATTACHMENT = 2L * 1024 * 1024

        val SUPPORTED_MIME_TYPES = listOf(
            NotePayload.MEME_TYPE_NOTE,
            "application/json",
            "application/octet-stream",
            "text/plain",
            "*/*",
        )
    }
}
