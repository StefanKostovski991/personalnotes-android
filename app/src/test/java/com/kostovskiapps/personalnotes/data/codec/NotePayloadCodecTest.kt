package com.kostovskiapps.personalnotes.data.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire format is the contract between two installations of the app, so it is covered by
 * plain JVM tests: no Android framework is involved by design.
 */
class NotePayloadCodecTest {

    private val sample = NotePayload(
        id = "local-42",
        title = "Groceries",
        description = "Milk, eggs and a really long line of text that must survive the round trip " +
            "including unicode: äöü, 日本語, emoji 🍋",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_500_000L,
        colorId = "amber",
        kindId = "mixed",
        location = NoteLocationPayload(label = "Vienna, Austria", latitude = 48.2082, longitude = 16.3738),
        attachments = listOf(
            NoteAttachmentPayload(
                name = "voice-1700000000000.m4a",
                mimeType = "audio/mp4",
                base64Data = NotePayloadCodec.base64UrlEncode(byteArrayOf(1, 2, 3, 4, 5)),
            ),
        ),
    )

    @Test
    fun `file format round trips every field`() {
        val bytes = NotePayloadCodec.encodeToFileBytes(sample)

        val decoded = NotePayloadCodec.parse(bytes)

        assertEquals(sample, decoded)
    }

    @Test
    fun `file starts with the magic header so it is recognisable on sight`() {
        val text = NotePayloadCodec.encodeToFileBytes(sample).toString(Charsets.UTF_8)

        assertTrue(text.startsWith(NotePayloadCodec.FILE_MAGIC))
        assertTrue(text.contains("\"schema\""))
        assertTrue(text.contains(NotePayload.SCHEMA))
    }

    /**
     * The key names are the interop contract: if the encoder writes a key the DTO does not
     * declare, decoding silently drops that field. This test pins the exact keys.
     */
    @Test
    fun `json keys match the documented wire format`() {
        val text = NotePayloadCodec.encodeToFileBytes(sample).toString(Charsets.UTF_8)

        listOf(
            "\"schema\"",
            "\"version\"",
            "\"id\"",
            "\"title\"",
            "\"description\"",
            "\"createdAt\"",
            "\"updatedAt\"",
            "\"colorId\"",
            "\"kindId\"",
            "\"location\"",
            "\"latitude\"",
            "\"attachments\"",
            "\"mimeType\"",
            "\"data\"",
        ).forEach { key ->
            assertTrue("expected the payload to contain $key", text.contains(key))
        }
    }

    /** Decodes a payload exactly as another installation of an older build would write it. */
    @Test
    fun `payload produced by an external writer is decoded without losing colour or kind`() {
        val external = """
            PERSONALNOTES-NOTE
            {
              "schema": "com.kostovskiapps.personalnotes/note",
              "version": 1,
              "id": "other-device-1",
              "title": "Amber spoken note",
              "description": "dictated elsewhere",
              "createdAt": 1750000000000,
              "updatedAt": 1750000500000,
              "colorId": "amber",
              "kindId": "spoken",
              "location": { "label": "Graz", "latitude": 47.07, "longitude": 15.43 },
              "attachments": []
            }
        """.trimIndent()

        val payload = NotePayloadCodec.parse(external.toByteArray(Charsets.UTF_8))

        assertEquals("amber", payload.colorId)
        assertEquals("spoken", payload.kindId)
        assertEquals("Graz", payload.location?.label)
    }

    @Test
    fun `parse accepts a bare json payload without the header`() {
        val compact = NotePayloadCodec.encodeToCompactBytes(sample)

        assertEquals(sample, NotePayloadCodec.parse(compact))
    }

    @Test
    fun `link token round trips and stays url safe`() {
        val token = NotePayloadCodec.encodeToLinkToken(sample)

        assertEquals(sample, NotePayloadCodec.decodeFromLinkToken(token))
        // Must be embeddable in a url path without escaping.
        assertTrue(token.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun `link token compresses repetitive text`() {
        val repetitive = sample.copy(description = "note ".repeat(400))

        val token = NotePayloadCodec.encodeToLinkToken(repetitive)
        val raw = NotePayloadCodec.encodeToCompactBytes(repetitive).size

        assertTrue("expected gzip to shrink the payload", token.length < raw)
    }

    @Test
    fun `fingerprint is stable for identical notes and differs when content changes`() {
        val same = sample.copy()
        val changedTitle = sample.copy(title = "Groceries 2")

        assertEquals(NotePayloadCodec.fingerprint(sample), NotePayloadCodec.fingerprint(same))
        assertNotEquals(NotePayloadCodec.fingerprint(sample), NotePayloadCodec.fingerprint(changedTitle))
    }

    @Test
    fun `fingerprint ignores transport-only fields`() {
        // The same note can arrive twice: once as a link, once as a file. Transfer details must
        // not change its identity, otherwise the second copy would be stored twice.
        val reimported = sample.copy(
            id = "local-99",
            attachments = emptyList(),
        )

        assertEquals(NotePayloadCodec.fingerprint(sample), NotePayloadCodec.fingerprint(reimported))
    }

    @Test
    fun `fingerprint distinguishes notes that differ only by place or colour`() {
        val moved = sample.copy(location = NoteLocationPayload(label = "Graz, Austria"))
        val recoloured = sample.copy(colorId = "lemon")

        assertNotEquals(NotePayloadCodec.fingerprint(sample), NotePayloadCodec.fingerprint(moved))
        assertNotEquals(NotePayloadCodec.fingerprint(sample), NotePayloadCodec.fingerprint(recoloured))
    }

    @Test(expected = Exception::class)
    fun `parse rejects a payload that is not a note`() {
        NotePayloadCodec.parse("{\"unrelated\":true}".toByteArray())
    }

    @Test
    fun `base64url handles every input length`() {
        for (size in 0..64) {
            val original = ByteArray(size) { (it * 7 + 3).toByte() }
            val encoded = NotePayloadCodec.base64UrlEncode(original)
            assertTrue(
                "unexpected characters for size $size",
                encoded.all { it.isLetterOrDigit() || it == '-' || it == '_' },
            )
            assertArrayEqualsForTest(original, NotePayloadCodec.base64UrlDecode(encoded))
        }
    }

    @Test
    fun `base64url decode accepts padded input`() {
        val original = byteArrayOf(10, 20, 30, 40)
        val encoded = NotePayloadCodec.base64UrlEncode(original)

        assertArrayEqualsForTest(original, NotePayloadCodec.base64UrlDecode("$encoded=="))
    }

    private fun assertArrayEqualsForTest(expected: ByteArray, actual: ByteArray) {
        assertEquals("length", expected.size, actual.size)
        expected.forEachIndexed { index, byte ->
            assertEquals("byte $index", byte, actual[index])
        }
    }
}
