package com.kairo.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTextReaderTest {
    @Test fun acceptsSupportedTextAndSourceFiles() {
        assertTrue(DocumentTextReader.isSupported("notes.md", "text/markdown"))
        assertTrue(DocumentTextReader.isSupported("source.kt", "text/plain"))
        assertTrue(DocumentTextReader.isSupported("data.json", "application/json"))
    }

    @Test fun rejectsImagesArchivesAndPdfFiles() {
        assertFalse(DocumentTextReader.isSupported("photo.png", "image/png"))
        assertFalse(DocumentTextReader.isSupported("app.apk", "application/vnd.android.package-archive"))
        assertFalse(DocumentTextReader.isSupported("report.pdf", "application/pdf"))
    }

    @Test fun decodesUtf8AndRemovesBom() {
        assertEquals("KAIRO document", DocumentTextReader.decodeText("\uFEFFKAIRO document".toByteArray(Charsets.UTF_8)))
    }

    @Test fun rejectsEmptyDocuments() {
        val error = runCatching { DocumentTextReader.decodeText(ByteArray(0)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test fun rejectsOversizedDocuments() {
        val bytes = ByteArray(DocumentTextReader.MAX_DOCUMENT_BYTES + 1) { 'x'.code.toByte() }
        val error = runCatching { DocumentTextReader.decodeText(bytes) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("96 KB"))
    }

    @Test fun rejectsMalformedUtf8() {
        val error = runCatching { DocumentTextReader.decodeText(byteArrayOf(0xC3.toByte())) }.exceptionOrNull()
        assertTrue(error is java.nio.charset.CharacterCodingException)
    }

    @Test fun rejectsBinaryNullCharacters() {
        val error = runCatching { DocumentTextReader.decodeText(byteArrayOf(0x41, 0x00, 0x42)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
