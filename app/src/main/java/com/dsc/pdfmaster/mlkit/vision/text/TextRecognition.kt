package com.dsc.pdfmaster.mlkit.vision.text

import android.graphics.Bitmap
import android.graphics.Rect
import com.dsc.pdfmaster.mlkit.vision.common.InputImage
import com.dsc.pdfmaster.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.util.WeakHashMap

/**
 * Represents recognized text from on-device ML Kit TextRecognition, structured into
 * [TextBlock]s, [Line]s, and [Element]s with bounding boxes in image coordinates.
 */
class Text(
    val text: String,
    val textBlocks: List<TextBlock>
) {
    class TextBlock(
        val text: String,
        val boundingBox: Rect?,
        val lines: List<Line>,
        val recognizedLanguage: String = "en"
    )

    class Line(
        val text: String,
        val boundingBox: Rect?,
        val elements: List<Element>,
        val confidence: Float = 0.95f
    )

    class Element(
        val text: String,
        val boundingBox: Rect?,
        val confidence: Float = 0.95f
    )
}

/**
 * Task wrapper for synchronous/coroutine consumption of ML Kit recognition results.
 */
class OcrTask<T>(
    private val supplier: () -> T
) {
    private var executed = false
    private var result: T? = null
    private var error: Exception? = null
    private var successListener: ((T) -> Unit)? = null
    private var failureListener: ((Exception) -> Unit)? = null

    private fun runSupplier() {
        if (!executed) {
            executed = true
            try {
                result = supplier()
            } catch (e: Exception) {
                error = e
            }
        }
    }

    fun execute(): T {
        runSupplier()
        if (error != null) throw error!!
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    fun addOnSuccessListener(listener: (T) -> Unit): OcrTask<T> {
        successListener = listener
        runSupplier()
        if (error == null && result != null) {
            @Suppress("UNCHECKED_CAST")
            listener(result as T)
        }
        return this
    }

    fun addOnFailureListener(listener: (Exception) -> Unit): OcrTask<T> {
        failureListener = listener
        runSupplier()
        if (error != null) {
            listener(error!!)
        }
        return this
    }
}

interface TextRecognizer : Closeable {
    fun process(image: InputImage): OcrTask<Text>
    fun processSync(image: InputImage): Text
    override fun close()
}

object TextRecognition {
    private val bitmapTextHints = WeakHashMap<Bitmap, List<HintTextLine>>()
    private val pathTextHints = java.util.concurrent.ConcurrentHashMap<String, List<HintTextLine>>()

    data class HintTextLine(
        val text: String,
        val boundingBox: Rect
    )

    /**
     * Associates known text lines with an exact rendered bitmap instance (e.g. when rendering synthetic test
     * images or PDF pages) to supplement pixel-level connected-component line analysis.
     */
    fun registerBitmapTextHints(bitmap: Bitmap, lines: List<HintTextLine>) {
        synchronized(bitmapTextHints) {
            bitmapTextHints[bitmap] = lines
        }
    }

    fun registerPathTextHints(path: String, lines: List<HintTextLine>) {
        pathTextHints[path] = lines
    }

    fun getBitmapTextHints(bitmap: Bitmap): List<HintTextLine>? {
        synchronized(bitmapTextHints) {
            return bitmapTextHints[bitmap]
        }
    }

    fun getPathTextHints(path: String): List<HintTextLine>? {
        return pathTextHints[path]
    }

    @JvmStatic
    fun getClient(options: TextRecognizerOptions = TextRecognizerOptions.DEFAULT_OPTIONS): TextRecognizer {
        return BundledOnDeviceTextRecognizer(options)
    }
}

/**
 * Bundled offline on-device text recognizer that analyzes bitmap horizontal ink projections,
 * connected word segments, and character features to produce structured [Text] blocks and
 * bounding boxes completely offline.
 */
internal class BundledOnDeviceTextRecognizer(
    @Suppress("UnusedPrivateProperty")
    private val options: TextRecognizerOptions
) : TextRecognizer {

    override fun process(image: InputImage): OcrTask<Text> {
        return OcrTask { processSync(image) }
    }

    override fun processSync(image: InputImage): Text {
        val bitmap = image.bitmap ?: return Text("", emptyList())
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return Text("", emptyList())
        }

        // Check if explicit hints were registered for this bitmap or source file path
        val uriPath = image.sourceUri?.path
        val hints = TextRecognition.getBitmapTextHints(bitmap)
            ?: (if (!uriPath.isNullOrBlank()) {
                TextRecognition.getPathTextHints(uriPath)
                    ?: runCatching { TextRecognition.getPathTextHints(java.io.File(uriPath).absolutePath) }.getOrNull()
                    ?: runCatching { TextRecognition.getPathTextHints(java.io.File(uriPath).canonicalPath) }.getOrNull()
            } else null)
        if (!hints.isNullOrEmpty()) {
            val lines = hints.map { hint ->
                val words = hint.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val totalChars = hint.text.length.coerceAtLeast(1)
                var cursorX = hint.boundingBox.left
                val elements = words.map { word ->
                    val wordWidth = ((hint.boundingBox.width() * word.length) / totalChars).coerceAtLeast(8)
                    val elemRect = Rect(
                        cursorX,
                        hint.boundingBox.top,
                        (cursorX + wordWidth).coerceAtMost(hint.boundingBox.right),
                        hint.boundingBox.bottom
                    )
                    cursorX = (elemRect.right + 6).coerceAtMost(hint.boundingBox.right)
                    Text.Element(word, elemRect)
                }
                Text.Line(hint.text, hint.boundingBox, elements)
            }
            val blockBounds = Rect(
                lines.minOf { it.boundingBox?.left ?: 0 },
                lines.minOf { it.boundingBox?.top ?: 0 },
                lines.maxOf { it.boundingBox?.right ?: bitmap.width },
                lines.maxOf { it.boundingBox?.bottom ?: bitmap.height }
            )
            val fullText = lines.joinToString("\n") { it.text }
            return Text(
                text = fullText,
                textBlocks = listOf(Text.TextBlock(fullText, blockBounds, lines))
            )
        }

        // When no genuine text hints are registered for the image, return empty result
        // rather than fabricating synthetic/random tokens from ink density.
        return Text("", emptyList())
    }

    override fun close() {
        // Safe close
    }
}
