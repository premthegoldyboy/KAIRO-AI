package com.kairo.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextReaderTest {
    @Test fun acceptsPdfWithinSizeLimit() {
        PdfTextReader.validateFileSize(1)
        PdfTextReader.validateFileSize(PdfTextReader.MAX_PDF_BYTES.toLong())
    }

    @Test fun rejectsEmptyAndOversizedPdf() {
        assertTrue(runCatching { PdfTextReader.validateFileSize(0) }.isFailure)
        assertTrue(runCatching {
            PdfTextReader.validateFileSize(PdfTextReader.MAX_PDF_BYTES.toLong() + 1)
        }.isFailure)
    }

    @Test fun acceptsOnlyOneToFivePages() {
        PdfTextReader.validatePageCount(1)
        PdfTextReader.validatePageCount(PdfTextReader.MAX_PAGES)
        assertTrue(runCatching { PdfTextReader.validatePageCount(0) }.isFailure)
        assertTrue(runCatching { PdfTextReader.validatePageCount(6) }.isFailure)
    }

    @Test fun extractedTextIsBoundedAndTrimmed() {
        val source = "  " + "x".repeat(PdfTextReader.MAX_EXTRACTED_CHARS + 100) + "  "
        val result = PdfTextReader.limitExtractedText(source)
        assertEquals(PdfTextReader.MAX_EXTRACTED_CHARS, result.length)
    }
}
