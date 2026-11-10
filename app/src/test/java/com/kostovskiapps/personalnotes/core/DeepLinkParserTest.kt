package com.kostovskiapps.personalnotes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deep links are the "send a note to another user" entry point, so every shape the app can
 * receive is pinned down here.
 */
class DeepLinkParserTest {

    @Test
    fun `embedded note link is recognised as inline payload`() {
        val result = DeepLinkParser.parse("personalnotes://note/d/ABC-_123")

        assertTrue(result is DeepLinkParser.Incoming.EmbeddedNote)
        assertEquals("ABC-_123", (result as DeepLinkParser.Incoming.EmbeddedNote).token)
        assertTrue(result.inline)
    }

    @Test
    fun `embedded note link works without the double slash`() {
        // Some share sheets strip the authority slashes from a custom scheme.
        val result = DeepLinkParser.parse("personalnotes:note/d/TOKEN")

        assertTrue(result is DeepLinkParser.Incoming.EmbeddedNote)
        assertEquals("TOKEN", (result as DeepLinkParser.Incoming.EmbeddedNote).token)
    }

    @Test
    fun `numeric note link opens a stored note`() {
        val result = DeepLinkParser.parse("personalnotes://note/42")

        assertEquals(DeepLinkParser.Incoming.LocalNote(42L), result)
    }

    @Test
    fun `https app link opens a stored note`() {
        val result = DeepLinkParser.parse("https://personalnotes.app/note/7")

        assertEquals(DeepLinkParser.Incoming.LocalNote(7L), result)
    }

    @Test
    fun `https app link can also carry the note`() {
        val result = DeepLinkParser.parse("https://personalnotes.app/note/d/PAYLOAD")

        assertEquals(
            DeepLinkParser.Incoming.EmbeddedNote("PAYLOAD", inline = true),
            result,
        )
    }

    @Test
    fun `import query parameter is understood`() {
        val result = DeepLinkParser.parse("personalnotes://import?d=QUERYTOKEN")

        assertEquals(
            DeepLinkParser.Incoming.EmbeddedNote("QUERYTOKEN", inline = false),
            result,
        )
    }

    @Test
    fun `note document uri is recognised by extension`() {
        val result = DeepLinkParser.parse(
            "content://com.android.providers.downloads/document/1234",
            mimeType = "application/vnd.personalnotes.note+json",
        )

        assertTrue(result is DeepLinkParser.Incoming.NoteDocument)
    }

    @Test
    fun `note document uri is recognised from the file name`() {
        val result = DeepLinkParser.parse("content://downloads/all_downloads/9/groceries.pnote")

        assertTrue(result is DeepLinkParser.Incoming.NoteDocument)
    }

    @Test
    fun `unrelated content uri is ignored`() {
        assertNull(
            DeepLinkParser.parse(
                "content://media/external/audio/media/55",
                mimeType = "audio/mpeg",
            ),
        )
    }

    @Test
    fun `blank and unrecognisable links are ignored`() {
        assertNull(DeepLinkParser.parse(null))
        assertNull(DeepLinkParser.parse(""))
        assertNull(DeepLinkParser.parse("hello"))
    }

    @Test
    fun `link without a path is ignored`() {
        assertNull(DeepLinkParser.parse("personalnotes://"))
    }

    @Test
    fun `segments helper strips scheme, query and fragment`() {
        assertEquals(
            listOf("note", "42"),
            LinkUtils.segments("personalnotes://note/42?utm=1#frag"),
        )
    }

    @Test
    fun `slug produces a safe file name fragment`() {
        assertEquals("my-first-note", LinkUtils.slug("My First Note!"))
        assertEquals("note", LinkUtils.slug("***"))
    }
}
