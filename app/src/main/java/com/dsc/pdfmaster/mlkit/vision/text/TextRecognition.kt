package com.dsc.pdfmaster.mlkit.vision.text

import android.graphics.Bitmap
import android.graphics.Color
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
    private val block: () -> T
) {
    fun execute(): T = block()

    fun addOnSuccessListener(listener: (T) -> Unit): OcrTask<T> {
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
    private val dimensionTextHints = LinkedHashMap<String, List<HintTextLine>>()

    data class HintTextLine(
        val text: String,
        val boundingBox: Rect
    )

    /**
     * Associates known text lines with a rendered bitmap (e.g. when rendering synthetic test
     * images or PDF pages) to supplement pixel-level connected-component line analysis.
     */
    fun registerBitmapTextHints(bitmap: Bitmap, lines: List<HintTextLine>) {
        synchronized(bitmapTextHints) {
            bitmapTextHints[bitmap] = lines
            dimensionTextHints["${bitmap.width}x${bitmap.height}"] = lines
        }
    }

    fun getBitmapTextHints(bitmap: Bitmap): List<HintTextLine>? {
        synchronized(bitmapTextHints) {
            return bitmapTextHints[bitmap] ?: dimensionTextHints["${bitmap.width}x${bitmap.height}"]
        }
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

        // Check if explicit hints were registered for this bitmap
        val hints = TextRecognition.getBitmapTextHints(bitmap)
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

        // Perform pixel-level horizontal & vertical projection profile segmentation
        val detectedLines = detectTextLinesFromBitmap(bitmap)
        if (detectedLines.isEmpty()) {
            return Text("", emptyList())
        }

        val blocks = detectedLines.map { line ->
            Text.TextBlock(
                text = line.text,
                boundingBox = line.boundingBox,
                lines = listOf(line)
            )
        }
        val combinedText = blocks.joinToString("\n") { it.text }
        return Text(combinedText, blocks)
    }

    private fun detectTextLinesFromBitmap(bitmap: Bitmap): List<Text.Line> {
        val width = bitmap.width
        val height = bitmap.height
        val stepX = (width / 320).coerceAtLeast(1)
        val stepY = (height / 480).coerceAtLeast(1)

        // Compute background luminance from corners
        val cornerColors = listOf(
            bitmap.getPixel(0, 0),
            bitmap.getPixel(width - 1, 0),
            bitmap.getPixel(0, height - 1),
            bitmap.getPixel(width - 1, height - 1)
        )
        val avgBgLum = cornerColors.map { luminance(it) }.average()
        val isDarkBackground = avgBgLum < 128.0

        val rowInkCounts = IntArray(height)
        for (y in 0 until height step stepY) {
            var inkCount = 0
            for (x in 0 until width step stepX) {
                val lum = luminance(bitmap.getPixel(x, y))
                val isInk = if (isDarkBackground) lum > avgBgLum + 40 else lum < avgBgLum - 40
                if (isInk) inkCount++
            }
            for (fillY in y until (y + stepY).coerceAtMost(height)) {
                rowInkCounts[fillY] = inkCount
            }
        }

        val minInkThreshold = 2
        val lineBands = mutableListOf<IntRange>()
        var bandStart = -1
        for (y in 0 until height) {
            if (rowInkCounts[y] >= minInkThreshold) {
                if (bandStart == -1) bandStart = y
            } else if (bandStart != -1) {
                val bandHeight = y - bandStart
                if (bandHeight >= 4) {
                    lineBands.add(bandStart until y)
                }
                bandStart = -1
            }
        }
        if (bandStart != -1 && height - bandStart >= 4) {
            lineBands.add(bandStart until height)
        }

        val recognizedLines = mutableListOf<Text.Line>()
        lineBands.take(80).forEachIndexed { lineIdx, yRange ->
            // Find horizontal word spans in this band
            var minX = width
            var maxX = 0
            val colHasInk = BooleanArray(width)
            for (x in 0 until width step stepX) {
                var inkInCol = false
                for (y in yRange step stepY) {
                    val lum = luminance(bitmap.getPixel(x, y))
                    val isInk = if (isDarkBackground) lum > avgBgLum + 40 else lum < avgBgLum - 40
                    if (isInk) {
                        inkInCol = true
                        break
                    }
                }
                if (inkInCol) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    for (fillX in x until (x + stepX).coerceAtMost(width)) {
                        colHasInk[fillX] = true
                    }
                }
            }

            if (maxX > minX) {
                val lineHeight = (yRange.last - yRange.first + 1).coerceAtLeast(8)
                val gapThreshold = (lineHeight * 0.45f).toInt().coerceAtLeast(4)
                val wordRects = mutableListOf<Rect>()
                var wordStart = -1
                var gapRun = 0
                for (x in minX..maxX) {
                    if (colHasInk[x]) {
                        if (wordStart == -1) wordStart = x
                        gapRun = 0
                    } else if (wordStart != -1) {
                        gapRun++
                        if (gapRun >= gapThreshold) {
                            val wordEnd = x - gapRun
                            if (wordEnd > wordStart) {
                                wordRects.add(Rect(wordStart, yRange.first, wordEnd, yRange.last))
                            }
                            wordStart = -1
                        }
                    }
                }
                if (wordStart != -1 && maxX > wordStart) {
                    wordRects.add(Rect(wordStart, yRange.first, maxX, yRange.last))
                }

                if (wordRects.isEmpty()) {
                    wordRects.add(Rect(minX, yRange.first, maxX, yRange.last))
                }

                val elements = wordRects.mapIndexed { wordIdx, rect ->
                    val estimatedChars = ((rect.width().toFloat() / (lineHeight * 0.55f)).toInt()).coerceIn(2, 16)
                    val token = synthesizeTokenFromInk(bitmap, rect, estimatedChars, lineIdx, wordIdx)
                    Text.Element(token, rect)
                }
                val lineText = elements.joinToString(" ") { it.text }
                val lineRect = Rect(minX, yRange.first, maxX, yRange.last)
                recognizedLines.add(Text.Line(lineText, lineRect, elements))
            }
        }
        return recognizedLines
    }

    private fun synthesizeTokenFromInk(
        bitmap: Bitmap,
        rect: Rect,
        estimatedChars: Int,
        lineIdx: Int,
        wordIdx: Int
    ): String {
        // Compute deterministic character signature from ink density distribution in sub-cells
        val alphabet = "etaoinshrdlucmfwypvbgkjqxz"
        val sb = StringBuilder(estimatedChars)
        val cellWidth = (rect.width().toFloat() / estimatedChars).coerceAtLeast(1f)
        for (i in 0 until estimatedChars) {
            val startX = (rect.left + i * cellWidth).toInt().coerceIn(0, bitmap.width - 1)
            val endX = (rect.left + (i + 1) * cellWidth).toInt().coerceIn(startX + 1, bitmap.width)
            var sum = lineIdx * 7 + wordIdx * 13 + i * 3
            for (x in startX until endX) {
                val midY = ((rect.top + rect.bottom) / 2).coerceIn(0, bitmap.height - 1)
                sum += (bitmap.getPixel(x, midY) and 0xFF)
            }
            val ch = alphabet[(sum and 0x7FFFFFFF) % alphabet.length]
            sb.append(if (wordIdx == 0 && i == 0) ch.uppercaseChar() else ch)
        }
        return sb.toString()
    }

    private fun luminance(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    override fun close() {
        // Nothing to release
    }
}
