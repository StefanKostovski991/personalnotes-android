package com.kostovskiapps.personalnotes.data.share

import android.content.Context
import android.net.Uri
import com.kostovskiapps.personalnotes.domain.repository.SharedAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Owns the app-private `files/attachments` directory where voice recordings live.
 *
 * File names (not absolute paths) are stored in the database: the app directory can move
 * between installs and the notes stay valid.
 */
class AttachmentStore(
    private val context: Context,
) {
    private val attachmentsDir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    private val cacheDir: File get() = File(context.cacheDir, DIR).apply { mkdirs() }

    fun newVoiceFile(): File {
        val stamp = System.currentTimeMillis()
        return File(cacheDir, "voice-$stamp.m4a").also { it.parentFile?.mkdirs() }
    }

    /** Moves a freshly recorded file from the cache into permanent storage. */
    fun persist(source: File): String {
        require(source.exists()) { "Recording is missing: ${source.name}" }
        val target = File(attachmentsDir, source.name)
        if (source.absolutePath == target.absolutePath) return target.name
        source.copyTo(target, overwrite = true)
        source.delete()
        return target.name
    }

    fun write(name: String, bytes: ByteArray): String {
        val safeName = sanitize(name)
        File(attachmentsDir, safeName).writeBytes(bytes)
        return safeName
    }

    fun open(fileRef: String): InputStream? {
        val file = resolve(fileRef) ?: return null
        return if (file.exists()) file.inputStream() else null
    }

    fun resolve(fileRef: String): File? {
        if (fileRef.isBlank() || fileRef.contains('/') || fileRef.contains("..")) return null
        return File(attachmentsDir, fileRef)
    }

    fun size(fileRef: String): Long = resolve(fileRef)?.takeIf { it.exists() }?.length() ?: 0L

    fun delete(fileRef: String) {
        resolve(fileRef)?.delete()
    }

    fun deleteAll(fileRefs: List<String>) {
        fileRefs.forEach(::delete)
    }

    /**
     * Copies an attachment into the cache so it can be handed to another app through a
     * content:// URI. A copy is required because the private files dir is not shareable.
     */
    fun toShared(fileRef: String, mimeType: String): SharedAttachment? {
        val source = resolve(fileRef)?.takeIf { it.exists() } ?: return null
        val outgoing = File(cacheDir, "share-${source.name}")
        source.copyTo(outgoing, overwrite = true)
        val uri: Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outgoing,
        )
        return SharedAttachment(
            uriString = uri.toString(),
            fileName = outgoing.name,
            mimeType = mimeType,
        )
    }

    suspend fun readBytes(fileRef: String): ByteArray? = withContext(Dispatchers.IO) {
        resolve(fileRef)?.takeIf { it.exists() }?.readBytes()
    }

    private fun sanitize(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "attachment" }
        return base.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    companion object {
        private const val DIR = "attachments"
    }
}
