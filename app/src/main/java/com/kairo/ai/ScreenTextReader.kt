package com.kairo.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads text only from an image the user explicitly selects; it never captures the screen silently. */
object ScreenTextReader {
    suspend fun extract(context: Context, uri: Uri): String = suspendCancellableCoroutine { continuation ->
        val image = runCatching { InputImage.fromFilePath(context, uri) }
            .getOrElse { continuation.resumeWithException(it); return@suspendCancellableCoroutine }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                recognizer.close()
                if (continuation.isActive) continuation.resume(text)
            }
            .addOnFailureListener { error ->
                recognizer.close()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
    }
}
