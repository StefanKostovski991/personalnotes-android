package com.kostovskiapps.personalnotes.data.codec

import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Converts a [NotePayload] to and from the three shapes it travels in:
 *
 * 1. a `.pnote` file - pretty printed JSON, human readable and diffable;
 * 2. a shareable URL fragment - gzipped + base64url so the link itself carries the note and
 *    the receiving app does not need the file (this is what "open the link" uses);
 * 3. a stable fingerprint - used to recognise a note that was already imported.
 *
 * No Android APIs are used here on purpose, so the whole format is covered by JVM unit tests.
 */
object NotePayloadCodec {

    /** Prefix that makes a `.pnote` file recognisable on sight. */
    const val FILE_MAGIC: String = "PERSONALNOTES-NOTE"

    /** Link path segment: `personalnotes://note/d/<compressed payload>`. */
    const val LINK_SEGMENT: String = "d"

    private const val MAX_DECOMPRESSED_BYTES = 4 * 1024 * 1024

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val compactJson = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encodeToFileBytes(payload: NotePayload): ByteArray {
        val text = FILE_MAGIC + "\n" + json.encodeToString(NotePayload.serializer(), payload) + "\n"
        return text.toByteArray(Charsets.UTF_8)
    }

    fun encodeToCompactBytes(payload: NotePayload): ByteArray =
        compactJson.encodeToString(NotePayload.serializer(), payload).toByteArray(Charsets.UTF_8)

    fun encodeToLinkToken(payload: NotePayload): String =
        base64UrlEncode(gzip(encodeToCompactBytes(payload)))

    /**
     * Accepts either a `.pnote` file body or a raw JSON payload. Kotlin's encoder does not
     * skip a comment-like first line, so the magic header is stripped before parsing.
     */
    fun parse(bytes: ByteArray): NotePayload {
        val text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF').trim()
        val body = if (text.startsWith(FILE_MAGIC)) {
            text.substring(FILE_MAGIC.length).trimStart('\r', '\n').trim()
        } else {
            text
        }
        return json.decodeFromString(NotePayload.serializer(), body)
    }

    fun decodeFromLinkToken(token: String): NotePayload {
        val inflated = gunzip(base64UrlDecode(token))
        return compactJson.decodeFromString(NotePayload.serializer(), inflated.toString(Charsets.UTF_8))
    }

    /**
     * Short, stable hash of everything that makes a note *this* note.
     *
     * Only content is hashed. Transfer details (the sender's local id, the attachment list) are
     * deliberately excluded, so the same note arriving twice - once as a link, once as a file -
     * produces one fingerprint and therefore one stored note.
     */
    fun fingerprint(payload: NotePayload): String {
        val material = buildString {
            append(payload.title.trim()).append('\u0000')
            append(payload.description.trim()).append('\u0000')
            append(payload.createdAt).append('\u0000')
            append(payload.colorId.orEmpty()).append('\u0000')
            append(payload.kindId.orEmpty()).append('\u0000')
            append(payload.location?.label.orEmpty())
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    // region base64url (RFC 4648 section 5, unpadded) - kept local to avoid android.util
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun base64UrlEncode(input: ByteArray): String {
        val out = StringBuilder((input.size + 2) / 3 * 4)
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xFF
            val b1 = if (i + 1 < input.size) input[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < input.size) input[i + 2].toInt() and 0xFF else -1

            out.append(ALPHABET[b0 shr 2])
            out.append(ALPHABET[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
            if (b1 >= 0) {
                out.append(ALPHABET[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 shr 6 else 0)])
            }
            if (b2 >= 0) {
                out.append(ALPHABET[b2 and 0x3F])
            }
            i += 3
        }
        return out.toString()
    }

    fun base64UrlDecode(text: String): ByteArray {
        val cleaned = text.trim().trimEnd('=')
        val out = ByteArrayOutputStream(cleaned.length * 3 / 4 + 3)
        var buffer = 0
        var bits = 0
        for (char in cleaned) {
            val value = ALPHABET.indexOf(char)
            require(value >= 0) { "Invalid base64url character '$char'" }
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
    // endregion

    private fun gzip(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(input.size / 2 + 32)
        GZIPOutputStream(out).use { it.write(input) }
        return out.toByteArray()
    }

    private fun gunzip(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(input.size * 4 + 64)
        val buffer = ByteArray(8 * 1024)
        var total = 0
        GZIPInputStream(input.inputStream()).use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_DECOMPRESSED_BYTES) { "Note payload is too large" }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }
}
