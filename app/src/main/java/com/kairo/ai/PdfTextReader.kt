package com.kairo.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * Bounded, on-device OCR for a user-selected PDF. No document is uploaded.
 * Text recognition uses ML Kit's Latin script model; scanned Telugu text may not be recognized.
 */
object PdfTextReader {
    const val MAX_PDF_BYTES = 5 * 1024 * 1024
    const val MAX_PAGES = 5
    const val MAX_RENDER_DIMENSION = 1800
    const val MAX_EXTRACTED_CHARS = 20_000

    internal fun validateFileSize(size: Long) {
        require(size in 1..MAX_PDF_BYTES.toLong()) {
            "Choose a PDF between 1 byte and 5 MB."
        }
    }

    internal fun validatePageCount(count: Int) {
        require(count in 1..MAX_PAGES) {
            "KAIRO can OCR PDFs with 1 to 5 pages. Split this document into smaller parts."
        }
    }

    internal fun limitExtractedText(text: String): String =
        text.take(MAX_EXTRACTED_CHARS).trim()

    suspend fun read(context: Context, uri: Uri): ImportedTextDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.takeIf { it.isNotBlank() }
            ?: "selected-document.pdf"
        require(name.endsWith(".pdf", ignoreCase = true) ||
            resolver.getType(uri)?.equals("application/pdf", ignoreCase = true) == true) {
            "Choose a PDF document."
        }

        val temp = File.createTempFile("kairo-pdf-", ".pdf", context.cacheDir)
        try {
            val input = resolver.openInputStream(uri)
                ?: throw IllegalStateException("KAIRO could not open the selected PDF.")
            val size = input.use { copyBounded(it, temp) }
            validateFileSize(size)

            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    validatePageCount(renderer.pageCount)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try {
                        val pages = ArrayList<String>(renderer.pageCount)
                        for (index in 0 until renderer.pageCount) {
                            currentCoroutineContext().ensureActive()
                            renderer.openPage(index).use { page ->
                                val scale = min(
                                    1f,
                                    MAX_RENDER_DIMENSION.toFloat() / max(page.width, page.height).toFloat()
                                )
                                val width = (page.width * scale).toInt().coerceAtLeast(1)
                                val height = (page.height * scale).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                try {
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    val recognized = withContext(NonCancellable) {
                                        recognizePage(recognizer, bitmap)
                                    }
                                    pages += "Page ${index + 1}\n${recognized}"
                                } catch (error: Throwable) {
                                    if (!bitmap.isRecycled) bitmap.recycle()
                                    throw error
                                }
                            }
                        }
                        val extracted = limitExtractedText(pages.joinToString("\n\n"))
                        require(extracted.isNotBlank()) {
                            "No readable text was found. This PDF may contain unsupported or low-quality scans."
                        }
                        ImportedTextDocument(name, extracted)
                    } finally {
                        recognizer.close()
                    }
                }
            }
        } finally {
            temp.delete()
        }
    }

    private fun copyBounded(input: InputStream, destination: File): Long {
        var total = 0L
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(8_192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_PDF_BYTES) { "The selected PDF is larger than 5 MB." }
                output.write(buffer, 0, count)
            }
        }
        return total
    }

    private suspend fun recognizePage(recognizer: TextRecognizer, bitmap: Bitmap): String {
        val task = try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
        } catch (error: Throwable) {
            if (!bitmap.isRecycled) bitmap.recycle()
            throw error
        }
        task.addOnCompleteListener {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        return suspendCancellableCoroutine { continuation ->
            task.addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result.text)
            }.addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }
}
