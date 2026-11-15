package com.kairo.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class ImportedTextDocument(val name: String, val text: String)

/**
 * Imports only a user-selected, bounded plain-text document.
 * It never scans storage or attempts to execute imported content.
 */
object DocumentTextReader {
    const val MAX_DOCUMENT_BYTES = 24 * 1024

    private val supportedExtensions = setOf(
        "txt", "md", "markdown", "csv", "json", "xml", "html", "htm",
        "kt", "kts", "java", "py", "js", "ts", "css", "yaml", "yml", "log"
    )
    private val supportedMimeTypes = setOf(
        "application/json", "application/xml", "text/xml",
        "application/x-yaml", "application/javascript", "text/javascript",
        "application/octet-stream"
    )

    internal fun isSupported(name: String, mimeType: String?): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension !in supportedExtensions) return false
        return mimeType.isNullOrBlank() ||
            mimeType.startsWith("text/") ||
            mimeType.lowercase() in supportedMimeTypes
    }

    internal fun decodeText(bytes: ByteArray): String {
        require(bytes.size <= MAX_DOCUMENT_BYTES) { "The selected document is larger than 24 KB." }
        val decoded = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
            .removePrefix("\uFEFF")
            .trim()
        require(decoded.isNotBlank()) { "The selected document is empty." }
        require('\u0000' !in decoded) { "The selected file is not plain text." }
        return decoded
    }

    suspend fun read(context: Context, uri: Uri): ImportedTextDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?.takeIf { it.isNotBlank() }
            ?: "selected-document.txt"
        val mimeType = resolver.getType(uri)
        require(isSupported(name, mimeType)) {
            "Choose a text document such as TXT, Markdown, CSV, JSON, XML, or a source-code file."
        }
        val bytes = resolver.openInputStream(uri)?.use(::readBounded)
            ?: throw IllegalStateException("KAIRO could not open the selected document.")
        ImportedTextDocument(name, decodeText(bytes))
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_DOCUMENT_BYTES) {
                "The selected document is larger than 24 KB. Choose a smaller text file."
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
