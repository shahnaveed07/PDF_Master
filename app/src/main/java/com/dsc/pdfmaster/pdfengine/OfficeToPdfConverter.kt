package com.dsc.pdfmaster.pdfengine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * Local, 100% offline OpenXML (.docx, .xlsx, .pptx) to PDF converter powered by PDFBox-Android
 * and Android's built-in ZIP + XmlPullParser APIs.
 *
 * - Word (.docx): Converts headings, styled paragraphs (bold/italic), bullet/numbered lists,
 *   alignment, multi-column tables with borders, and embedded images into paginated A4 PDF pages.
 * - Excel (.xlsx): Converts worksheets, shared strings, inline strings, numbers/dates, cached
 *   formula results, and tables into paginated grid tables (landscape/portrait A4).
 * - PowerPoint (.pptx): Converts slides, titles, subtitles, hierarchical bullet text, tables,
 *   embedded slide images, and speaker notes into landscape slide PDF pages.
 * - Explicitly detects and discloses unsupported legacy binary (.doc, .xls, .ppt) OLE2 files,
 *   encrypted Office packages, wrong Office document types, and corrupted/empty files.
 */
object OfficeToPdfConverter {

    enum class OfficeDocumentType(
        val label: String,
        val expectedExtension: String,
        val layoutDisclosure: String
    ) {
        WORD_DOCX(
            label = "Word (.docx)",
            expectedExtension = "docx",
            layoutDisclosure = "Local offline conversion preserves headings, paragraphs, bold/italic text, lists, tables, and embedded images. Floating shapes, custom fonts, macros, and legacy binary .doc files are not supported."
        ),
        EXCEL_XLSX(
            label = "Excel (.xlsx)",
            expectedExtension = "xlsx",
            layoutDisclosure = "Local offline conversion renders worksheets, cells, formatted numbers/dates, and cached formula values as paginated grid tables. Charts, pivot tables, live formula recalculation, and legacy binary .xls files are not supported."
        ),
        POWERPOINT_PPTX(
            label = "PowerPoint (.pptx)",
            expectedExtension = "pptx",
            layoutDisclosure = "Local offline conversion renders slides, titles, bullet points, slide tables, embedded images, and speaker notes onto landscape PDF pages. Animations, SmartArt vectors, custom master themes, and legacy binary .ppt files are not supported."
        )
    }

    enum class OfficeConversionError {
        FILE_NOT_FOUND_OR_EMPTY,
        LEGACY_BINARY_FORMAT_UNSUPPORTED,
        PASSWORD_PROTECTED_OFFICE_FILE,
        INVALID_OR_CORRUPTED_PACKAGE,
        WRONG_OFFICE_DOCUMENT_TYPE,
        EMPTY_DOCUMENT_CONTENT,
        OUTPUT_WRITE_FAILED,
        OUTPUT_VALIDATION_FAILED
    }

    sealed class OfficeConversionResult {
        data class Success(
            val file: File,
            val pageCount: Int,
            val fileSizeBytes: Long,
            val documentType: OfficeDocumentType,
            val summary: String,
            val layoutDisclosure: String
        ) : OfficeConversionResult()

        data class Error(
            val error: OfficeConversionError,
            val message: String
        ) : OfficeConversionResult()
    }

    data class OfficeSourceInspection(
        val isValid: Boolean,
        val detectedType: OfficeDocumentType?,
        val previewSummary: String,
        val error: OfficeConversionError? = null,
        val errorMessage: String? = null
    )

    private val OLE2_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
    )

    private val ZIP_MAGIC = byteArrayOf(
        0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte()
    )

    private const val MAX_ARCHIVE_BYTES = 30 * 1024 * 1024 // 30 MB safety limit
    private const val MAX_ENTRY_BYTES = 15 * 1024 * 1024 // 15 MB per entry safety limit

    private fun openUriInputStream(context: Context, uri: Uri): InputStream? {
        return when (uri.scheme) {
            "file" -> uri.path?.let { File(it) }?.takeIf { it.exists() && it.isFile }?.inputStream()
            else -> context.contentResolver.openInputStream(uri)
        }
    }

    private fun readRawBytesFromUri(context: Context, uri: Uri): ByteArray? {
        return try {
            openUriInputStream(context, uri)?.use { input ->
                val baos = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read == -1) break
                    total += read
                    if (total > MAX_ARCHIVE_BYTES) return null
                    baos.write(buf, 0, read)
                }
                baos.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun startsWithSignature(bytes: ByteArray, signature: ByteArray): Boolean {
        if (bytes.size < signature.size) return false
        for (i in signature.indices) {
            if (bytes[i] != signature[i]) return false
        }
        return true
    }

    private fun isEncryptedOle2Package(bytes: ByteArray): Boolean {
        if (!startsWithSignature(bytes, OLE2_MAGIC)) return false
        val scanLen = minOf(bytes.size, 65536)
        val ascii = String(bytes, 0, scanLen, Charsets.ISO_8859_1)
        val utf16 = try {
            String(bytes, 0, scanLen, Charsets.UTF_16LE)
        } catch (_: Exception) {
            ""
        }
        return ascii.contains("EncryptedPackage") ||
            ascii.contains("StrongEncryptionDataSpace") ||
            ascii.contains("DRMEncrypted") ||
            utf16.contains("EncryptedPackage") ||
            utf16.contains("StrongEncryptionDataSpace")
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray>? {
        if (!startsWithSignature(bytes, ZIP_MAGIC)) return null
        val entries = LinkedHashMap<String, ByteArray>()
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val baos = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var entryTotal = 0
                        while (true) {
                            val r = zis.read(buf)
                            if (r == -1) break
                            entryTotal += r
                            if (entryTotal > MAX_ENTRY_BYTES) break
                            baos.write(buf, 0, r)
                        }
                        val normalizedName = entry.name.trimStart('/').replace('\\', '/')
                        entries[normalizedName] = baos.toByteArray()
                    }
                    zis.closeEntry()
                }
            }
            if (entries.isEmpty()) null else entries
        } catch (e: Exception) {
            null
        }
    }

    private fun detectOfficeTypeFromEntries(entries: Map<String, ByteArray>): OfficeDocumentType? {
        return when {
            entries.containsKey("word/document.xml") -> OfficeDocumentType.WORD_DOCX
            entries.containsKey("xl/workbook.xml") || entries.keys.any { it.startsWith("xl/worksheets/sheet") } -> OfficeDocumentType.EXCEL_XLSX
            entries.containsKey("ppt/presentation.xml") || entries.keys.any { it.startsWith("ppt/slides/slide") } -> OfficeDocumentType.POWERPOINT_PPTX
            else -> null
        }
    }

    /**
     * Inspects an Office file URI before conversion to validate format, detect legacy binary
     * (.doc/.xls/.ppt) or password-protected Office files, and provide a quick content summary.
     */
    suspend fun inspectOfficeSource(
        context: Context,
        uri: Uri,
        expectedType: OfficeDocumentType
    ): OfficeSourceInspection = withContext(Dispatchers.IO) {
        val bytes = readRawBytesFromUri(context, uri)
        if (bytes == null || bytes.isEmpty()) {
            return@withContext OfficeSourceInspection(
                isValid = false,
                detectedType = null,
                previewSummary = "",
                error = OfficeConversionError.FILE_NOT_FOUND_OR_EMPTY,
                errorMessage = "Selected file does not exist, is empty, or could not be read."
            )
        }

        if (startsWithSignature(bytes, OLE2_MAGIC)) {
            if (isEncryptedOle2Package(bytes)) {
                return@withContext OfficeSourceInspection(
                    isValid = false,
                    detectedType = null,
                    previewSummary = "",
                    error = OfficeConversionError.PASSWORD_PROTECTED_OFFICE_FILE,
                    errorMessage = "This ${expectedType.label} document is password-protected. Please remove the password in your Office editor before converting."
                )
            }
            val legacyExt = when (expectedType) {
                OfficeDocumentType.WORD_DOCX -> ".doc (Word 97–2003)"
                OfficeDocumentType.EXCEL_XLSX -> ".xls (Excel 97–2003)"
                OfficeDocumentType.POWERPOINT_PPTX -> ".ppt (PowerPoint 97–2003)"
            }
            return@withContext OfficeSourceInspection(
                isValid = false,
                detectedType = null,
                previewSummary = "",
                error = OfficeConversionError.LEGACY_BINARY_FORMAT_UNSUPPORTED,
                errorMessage = "Legacy binary $legacyExt format is not supported offline. Please select a modern .${expectedType.expectedExtension} file."
            )
        }

        val entries = readZipEntries(bytes)
            ?: return@withContext OfficeSourceInspection(
                isValid = false,
                detectedType = null,
                previewSummary = "",
                error = OfficeConversionError.INVALID_OR_CORRUPTED_PACKAGE,
                errorMessage = "Selected file is not a valid .${expectedType.expectedExtension} OpenXML document."
            )

        val detectedType = detectOfficeTypeFromEntries(entries)
            ?: return@withContext OfficeSourceInspection(
                isValid = false,
                detectedType = null,
                previewSummary = "",
                error = OfficeConversionError.INVALID_OR_CORRUPTED_PACKAGE,
                errorMessage = "Archive does not contain valid ${expectedType.label} OpenXML document structure."
            )

        if (detectedType != expectedType) {
            return@withContext OfficeSourceInspection(
                isValid = false,
                detectedType = detectedType,
                previewSummary = "",
                error = OfficeConversionError.WRONG_OFFICE_DOCUMENT_TYPE,
                errorMessage = "Selected file is a ${detectedType.label} file, but ${expectedType.label} conversion was selected."
            )
        }

        val summary = when (expectedType) {
            OfficeDocumentType.WORD_DOCX -> {
                val docModel = parseDocxDocument(entries)
                val blockCount = docModel.blocks.size
                if (blockCount == 0) {
                    return@withContext OfficeSourceInspection(
                        isValid = false,
                        detectedType = detectedType,
                        previewSummary = "",
                        error = OfficeConversionError.EMPTY_DOCUMENT_CONTENT,
                        errorMessage = "The selected Word (.docx) file contains no readable text, tables, or images."
                    )
                }
                val firstText = docModel.blocks.filterIsInstance<DocxBlock.Paragraph>()
                    .firstOrNull { it.plainText.isNotBlank() }?.plainText?.take(120) ?: "Contains tables/images"
                "Word .docx verified ($blockCount section blocks) — $firstText"
            }
            OfficeDocumentType.EXCEL_XLSX -> {
                val sheets = parseXlsxWorkbook(entries)
                val nonEmptySheets = sheets.filter { it.rows.isNotEmpty() }
                if (nonEmptySheets.isEmpty()) {
                    return@withContext OfficeSourceInspection(
                        isValid = false,
                        detectedType = detectedType,
                        previewSummary = "",
                        error = OfficeConversionError.EMPTY_DOCUMENT_CONTENT,
                        errorMessage = "The selected Excel (.xlsx) workbook contains no populated worksheet cells."
                    )
                }
                val totalRows = nonEmptySheets.sumOf { it.rows.size }
                "Excel .xlsx verified (${nonEmptySheets.size} sheet(s), $totalRows populated rows: ${nonEmptySheets.joinToString { it.name }})"
            }
            OfficeDocumentType.POWERPOINT_PPTX -> {
                val slides = parsePptxPresentation(entries)
                if (slides.isEmpty() || slides.all { it.isEmpty() }) {
                    return@withContext OfficeSourceInspection(
                        isValid = false,
                        detectedType = detectedType,
                        previewSummary = "",
                        error = OfficeConversionError.EMPTY_DOCUMENT_CONTENT,
                        errorMessage = "The selected PowerPoint (.pptx) presentation contains no readable slides."
                    )
                }
                "PowerPoint .pptx verified (${slides.size} slide(s))"
            }
        }

        OfficeSourceInspection(
            isValid = true,
            detectedType = detectedType,
            previewSummary = summary
        )
    }

    /**
     * Converts a Word (.docx), Excel (.xlsx), or PowerPoint (.pptx) document URI to a validated PDF file locally.
     */
    suspend fun convertOfficeToPdfDetailed(
        context: Context,
        uri: Uri,
        expectedType: OfficeDocumentType,
        outputName: String,
        outputDir: File = File(context.filesDir, "output_pdfs")
    ): OfficeConversionResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()

        if ((outputDir.exists() && !outputDir.isDirectory) || (!outputDir.exists() && !outputDir.mkdirs()) || !outputDir.canWrite()) {
            return@withContext OfficeConversionResult.Error(
                OfficeConversionError.OUTPUT_WRITE_FAILED,
                "Destination directory for converted PDF files is not writable."
            )
        }

        val bytes = readRawBytesFromUri(context, uri)
        if (bytes == null || bytes.isEmpty()) {
            return@withContext OfficeConversionResult.Error(
                OfficeConversionError.FILE_NOT_FOUND_OR_EMPTY,
                "Selected file does not exist, is empty, or could not be read."
            )
        }

        if (startsWithSignature(bytes, OLE2_MAGIC)) {
            if (isEncryptedOle2Package(bytes)) {
                return@withContext OfficeConversionResult.Error(
                    OfficeConversionError.PASSWORD_PROTECTED_OFFICE_FILE,
                    "This ${expectedType.label} document is password-protected. Please unlock it before converting to PDF."
                )
            }
            val legacyExt = when (expectedType) {
                OfficeDocumentType.WORD_DOCX -> ".doc (Word 97–2003)"
                OfficeDocumentType.EXCEL_XLSX -> ".xls (Excel 97–2003)"
                OfficeDocumentType.POWERPOINT_PPTX -> ".ppt (PowerPoint 97–2003)"
            }
            return@withContext OfficeConversionResult.Error(
                OfficeConversionError.LEGACY_BINARY_FORMAT_UNSUPPORTED,
                "Legacy binary $legacyExt format is not supported offline. Please select a modern .${expectedType.expectedExtension} file."
            )
        }

        val entries = readZipEntries(bytes)
            ?: return@withContext OfficeConversionResult.Error(
                OfficeConversionError.INVALID_OR_CORRUPTED_PACKAGE,
                "Selected file is not a valid .${expectedType.expectedExtension} OpenXML document."
            )

        val detectedType = detectOfficeTypeFromEntries(entries)
            ?: return@withContext OfficeConversionResult.Error(
                OfficeConversionError.INVALID_OR_CORRUPTED_PACKAGE,
                "Selected file does not contain valid ${expectedType.label} OpenXML content."
            )

        if (detectedType != expectedType) {
            return@withContext OfficeConversionResult.Error(
                OfficeConversionError.WRONG_OFFICE_DOCUMENT_TYPE,
                "Selected file is a ${detectedType.label} document, not a ${expectedType.label} document."
            )
        }

        val tempPdf = File(context.cacheDir, "temp_office2pdf_${System.nanoTime()}.pdf")
        var finalOutput: File? = null
        var doc: PDDocument? = null

        try {
            PDFBoxResourceLoader.init(context)
            doc = PDDocument()

            val summaryText = when (expectedType) {
                OfficeDocumentType.WORD_DOCX -> {
                    val docxModel = parseDocxDocument(entries)
                    if (docxModel.blocks.isEmpty()) {
                        return@withContext OfficeConversionResult.Error(
                            OfficeConversionError.EMPTY_DOCUMENT_CONTENT,
                            "The selected Word (.docx) document contains no readable text, tables, or images."
                        )
                    }
                    renderDocxToPdDocument(doc, docxModel, entries)
                    "Converted Word (.docx) document (${docxModel.blocks.size} content blocks)"
                }
                OfficeDocumentType.EXCEL_XLSX -> {
                    val sheets = parseXlsxWorkbook(entries).filter { it.rows.isNotEmpty() }
                    if (sheets.isEmpty()) {
                        return@withContext OfficeConversionResult.Error(
                            OfficeConversionError.EMPTY_DOCUMENT_CONTENT,
                            "The selected Excel (.xlsx) workbook contains no populated worksheet cells."
                        )
                    }
                    renderXlsxToPdDocument(doc, sheets)
                    val totalRows = sheets.sumOf { it.rows.size }
                    "Converted Excel (.xlsx) workbook (${sheets.size} worksheet(s), $totalRows rows)"
                }
                OfficeDocumentType.POWERPOINT_PPTX -> {
                    val slides = parsePptxPresentation(entries).filter { !it.isEmpty() }
                    if (slides.isEmpty()) {
                        return@withContext OfficeConversionResult.Error(
                            OfficeConversionError.EMPTY_DOCUMENT_CONTENT,
                            "The selected PowerPoint (.pptx) presentation contains no readable slide content."
                        )
                    }
                    renderPptxToPdDocument(doc, slides, entries)
                    "Converted PowerPoint (.pptx) presentation (${slides.size} slide(s))"
                }
            }

            val generatedPageCount = doc.numberOfPages
            if (generatedPageCount <= 0) {
                return@withContext OfficeConversionResult.Error(
                    OfficeConversionError.EMPTY_DOCUMENT_CONTENT,
                    "No PDF pages were generated because the document had no renderable content."
                )
            }

            coroutineContext.ensureActive()

            doc.save(tempPdf)
            doc.close()
            doc = null

            if (!validatePdfFile(tempPdf, expectedMinPages = 1)) {
                tempPdf.delete()
                return@withContext OfficeConversionResult.Error(
                    OfficeConversionError.OUTPUT_VALIDATION_FAILED,
                    "Generated PDF failed validation."
                )
            }

            val cleanOutputName = outputName.trim().ifBlank { "Converted_${expectedType.expectedExtension}.pdf" }
            finalOutput = PdfEngine.getUniqueOutputFile(outputDir, cleanOutputName)
            tempPdf.copyTo(finalOutput, overwrite = true)
            tempPdf.delete()

            if (!validatePdfFile(finalOutput, expectedMinPages = generatedPageCount)) {
                finalOutput.delete()
                return@withContext OfficeConversionResult.Error(
                    OfficeConversionError.OUTPUT_VALIDATION_FAILED,
                    "Saved PDF file failed final validation."
                )
            }

            OfficeConversionResult.Success(
                file = finalOutput,
                pageCount = generatedPageCount,
                fileSizeBytes = finalOutput.length(),
                documentType = expectedType,
                summary = summaryText,
                layoutDisclosure = expectedType.layoutDisclosure
            )
        } catch (e: CancellationException) {
            tempPdf.delete()
            finalOutput?.delete()
            throw e
        } catch (e: Throwable) {
            tempPdf.delete()
            finalOutput?.delete()
            OfficeConversionResult.Error(
                OfficeConversionError.OUTPUT_WRITE_FAILED,
                "Failed to convert ${expectedType.label} to PDF: ${e.localizedMessage ?: "Unexpected error"}"
            )
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
            tempPdf.delete()
        }
    }

    private fun validatePdfFile(file: File, expectedMinPages: Int): Boolean {
        if (!file.exists() || !file.isFile || file.length() == 0L || !PdfEngine.hasPdfHeader(file)) {
            return false
        }
        return try {
            PDDocument.load(file).use { loaded ->
                loaded.numberOfPages >= expectedMinPages
            }
        } catch (e: Throwable) {
            false
        }
    }

    // =========================================================================
    // Typography & Text Helpers for PDFBox PDType1Font (WinAnsiEncoding safe)
    // =========================================================================

    private fun sanitizeForPdfFont(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '\u2018', '\u2019', '\u201A', '\u2032' -> sb.append('\'')
                '\u201C', '\u201D', '\u201E', '\u2033' -> sb.append('"')
                '\u2013', '\u2014', '\u2212' -> sb.append('-')
                '\u2026' -> sb.append("...")
                '\u2022', '\u2023', '\u25E6', '\u2043', '\u25AA', '\u25CF' -> sb.append("* ")
                '\u00A0', '\u2007', '\u202F' -> sb.append(' ')
                '\t' -> sb.append("    ")
                else -> {
                    val code = ch.code
                    if (code in 32..126 || code in 161..255) {
                        sb.append(ch)
                    } else if (code > 255) {
                        // Fallback for non-WinAnsi glyphs
                        sb.append('?')
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun measureTextWidth(font: PDFont, fontSize: Float, text: String): Float {
        if (text.isEmpty()) return 0f
        val safe = sanitizeForPdfFont(text)
        return try {
            font.getStringWidth(safe) / 1000f * fontSize
        } catch (e: Exception) {
            safe.length * fontSize * 0.55f
        }
    }

    private fun wrapTextToLines(
        text: String,
        font: PDFont,
        fontSize: Float,
        maxWidth: Float
    ): List<String> {
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        val result = mutableListOf<String>()
        for (rawLine in normalized.split("\n")) {
            val cleanLine = sanitizeForPdfFont(rawLine)
            if (cleanLine.isBlank()) {
                result.add("")
                continue
            }
            val words = cleanLine.split(" ")
            var current = ""
            for (word in words) {
                if (word.isEmpty()) continue
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (measureTextWidth(font, fontSize, candidate) <= maxWidth) {
                    current = candidate
                } else {
                    if (current.isNotEmpty()) {
                        result.add(current)
                        current = ""
                    }
                    // If single word exceeds maxWidth, break it character by character
                    if (measureTextWidth(font, fontSize, word) <= maxWidth) {
                        current = word
                    } else {
                        var chunk = ""
                        for (c in word) {
                            val nextChunk = "$chunk$c"
                            if (measureTextWidth(font, fontSize, nextChunk) <= maxWidth) {
                                chunk = nextChunk
                            } else {
                                if (chunk.isNotEmpty()) result.add(chunk)
                                chunk = c.toString()
                            }
                        }
                        current = chunk
                    }
                }
            }
            if (current.isNotEmpty()) {
                result.add(current)
            }
        }
        return if (result.isEmpty()) listOf("") else result
    }

    private fun drawSafeText(
        cs: PDPageContentStream,
        font: PDFont,
        fontSize: Float,
        x: Float,
        y: Float,
        text: String,
        r: Int = 25,
        g: Int = 25,
        b: Int = 28
    ) {
        val safe = sanitizeForPdfFont(text)
        if (safe.isEmpty()) return
        try {
            cs.setNonStrokingColor(r, g, b)
            cs.beginText()
            cs.setFont(font, fontSize)
            cs.newLineAtOffset(x, y)
            cs.showText(safe)
            cs.endText()
        } catch (_: Exception) {
        }
    }

    private fun decodeEmbeddedImageToPdImage(
        doc: PDDocument,
        imageBytes: ByteArray
    ): Pair<com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject, Pair<Int, Int>>? {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
            val width = bitmap.width.coerceAtLeast(1)
            val height = bitmap.height.coerceAtLeast(1)
            val flattened = if (bitmap.hasAlpha()) {
                val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(flat)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                bitmap.recycle()
                flat
            } else {
                bitmap
            }
            try {
                val pdImg = try {
                    val jpegBytes = ByteArrayOutputStream().use { baos ->
                        flattened.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                        baos.toByteArray()
                    }
                    if (jpegBytes.isNotEmpty()) {
                        JPEGFactory.createFromByteArray(doc, jpegBytes)
                    } else {
                        LosslessFactory.createFromImage(doc, flattened)
                    }
                } catch (_: Throwable) {
                    LosslessFactory.createFromImage(doc, flattened)
                }
                pdImg to (width to height)
            } finally {
                flattened.recycle()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun newXmlPullParser(xmlBytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        return factory.newPullParser().apply {
            setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        }
    }

    private fun localTagName(rawName: String?): String {
        if (rawName == null) return ""
        val colonIdx = rawName.indexOf(':')
        return if (colonIdx >= 0) rawName.substring(colonIdx + 1) else rawName
    }

    private fun getAttrByLocalName(parser: XmlPullParser, attrLocalName: String): String? {
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i)
            if (name == attrLocalName || localTagName(name) == attrLocalName) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun parseRelationships(relsBytes: ByteArray?): Map<String, String> {
        if (relsBytes == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        return try {
            val parser = newXmlPullParser(relsBytes)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && localTagName(parser.name) == "Relationship") {
                    val id = getAttrByLocalName(parser, "Id")
                    val target = getAttrByLocalName(parser, "Target")
                    if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                        map[id] = target
                    }
                }
                event = parser.next()
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun resolveZipPath(baseDir: String, relativeTarget: String): String {
        val cleaned = relativeTarget.trim().replace('\\', '/')
        if (cleaned.startsWith("/")) return cleaned.trimStart('/')
        val parts = (baseDir.split("/") + cleaned.split("/")).filter { it.isNotEmpty() }
        val resolved = ArrayDeque<String>()
        for (part in parts) {
            when (part) {
                "." -> {}
                ".." -> if (resolved.isNotEmpty()) resolved.removeLast()
                else -> resolved.addLast(part)
            }
        }
        return resolved.joinToString("/")
    }

    // =========================================================================
    // 1. WORD (.DOCX) PARSER & PDF RENDERER
    // =========================================================================

    private sealed class DocxBlock {
        data class Paragraph(
            val plainText: String,
            val headingLevel: Int, // 0 = body, 1..4 = H1..H4, 9 = Title
            val isBold: Boolean,
            val isItalic: Boolean,
            val isListItem: Boolean,
            val listLevel: Int,
            val alignment: String, // "left", "center", "right"
            val fontSizePt: Float?,
            val pageBreakBefore: Boolean,
            val embeddedImagePaths: List<String>
        ) : DocxBlock()

        data class Table(
            val rows: List<List<String>>
        ) : DocxBlock()
    }

    private data class DocxDocumentModel(
        val blocks: List<DocxBlock>
    )

    private fun parseDocxStyles(stylesBytes: ByteArray?): Map<String, Int> {
        if (stylesBytes == null) return emptyMap()
        val styleToHeading = mutableMapOf<String, Int>()
        return try {
            val parser = newXmlPullParser(stylesBytes)
            var currentStyleId: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (localTagName(parser.name)) {
                            "style" -> {
                                currentStyleId = getAttrByLocalName(parser, "styleId")
                                val idLower = currentStyleId?.lowercase(Locale.US) ?: ""
                                when {
                                    idLower == "title" -> currentStyleId?.let { styleToHeading[it] = 9 }
                                    idLower == "subtitle" -> currentStyleId?.let { styleToHeading[it] = 2 }
                                    idLower.startsWith("heading1") || idLower == "h1" -> currentStyleId?.let { styleToHeading[it] = 1 }
                                    idLower.startsWith("heading2") || idLower == "h2" -> currentStyleId?.let { styleToHeading[it] = 2 }
                                    idLower.startsWith("heading3") || idLower == "h3" -> currentStyleId?.let { styleToHeading[it] = 3 }
                                    idLower.startsWith("heading") -> currentStyleId?.let { styleToHeading[it] = 4 }
                                }
                            }
                            "name" -> {
                                val styleId = currentStyleId
                                val valAttr = getAttrByLocalName(parser, "val")?.lowercase(Locale.US) ?: ""
                                if (styleId != null) {
                                    when {
                                        valAttr == "title" -> styleToHeading[styleId] = 9
                                        valAttr == "subtitle" -> styleToHeading[styleId] = 2
                                        valAttr == "heading 1" || valAttr == "heading1" -> styleToHeading[styleId] = 1
                                        valAttr == "heading 2" || valAttr == "heading2" -> styleToHeading[styleId] = 2
                                        valAttr == "heading 3" || valAttr == "heading3" -> styleToHeading[styleId] = 3
                                        valAttr.startsWith("heading") -> styleToHeading[styleId] = 4
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (localTagName(parser.name) == "style") {
                            currentStyleId = null
                        }
                    }
                }
                event = parser.next()
            }
            styleToHeading
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseDocxDocument(entries: Map<String, ByteArray>): DocxDocumentModel {
        val docBytes = entries["word/document.xml"] ?: return DocxDocumentModel(emptyList())
        val rels = parseRelationships(entries["word/_rels/document.xml.rels"])
        val styleHeadings = parseDocxStyles(entries["word/styles.xml"])

        val blocks = mutableListOf<DocxBlock>()
        try {
            val parser = newXmlPullParser(docBytes)
            var event = parser.eventType

            var tableDepth = 0
            var currentTableRows = mutableListOf<List<String>>()
            var currentRowCells = mutableListOf<String>()
            var currentCellText = StringBuilder()

            var inParagraph = false
            var pText = StringBuilder()
            var pHeading = 0
            var pBold = false
            var pItalic = false
            var pHasNonBoldRun = false
            var pList = false
            var pListLevel = 0
            var pAlign = "left"
            var pFontSize: Float? = null
            var pPageBreak = false
            var pImages = mutableListOf<String>()

            var inRun = false
            var rBold = false
            var rItalic = false
            var rSize: Float? = null
            var inText = false

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = localTagName(parser.name)
                        when (tag) {
                            "tbl" -> {
                                tableDepth++
                                if (tableDepth == 1) {
                                    currentTableRows = mutableListOf()
                                }
                            }
                            "tr" -> {
                                if (tableDepth == 1) {
                                    currentRowCells = mutableListOf()
                                }
                            }
                            "tc" -> {
                                if (tableDepth == 1) {
                                    currentCellText = StringBuilder()
                                }
                            }
                            "p" -> {
                                if (tableDepth == 0) {
                                    inParagraph = true
                                    pText = StringBuilder()
                                    pHeading = 0
                                    pBold = false
                                    pItalic = false
                                    pHasNonBoldRun = false
                                    pList = false
                                    pListLevel = 0
                                    pAlign = "left"
                                    pFontSize = null
                                    pPageBreak = false
                                    pImages = mutableListOf()
                                } else if (tableDepth == 1 && currentCellText.isNotEmpty()) {
                                    currentCellText.append("\n")
                                }
                            }
                            "pStyle" -> {
                                val styleVal = getAttrByLocalName(parser, "val") ?: ""
                                if (tableDepth == 0 && inParagraph) {
                                    val mapped = styleHeadings[styleVal]
                                    if (mapped != null) {
                                        pHeading = mapped
                                    } else {
                                        val lower = styleVal.lowercase(Locale.US)
                                        when {
                                            lower == "title" -> pHeading = 9
                                            lower == "subtitle" -> pHeading = 2
                                            lower.startsWith("heading1") || lower == "h1" -> pHeading = 1
                                            lower.startsWith("heading2") || lower == "h2" -> pHeading = 2
                                            lower.startsWith("heading3") || lower == "h3" -> pHeading = 3
                                            lower.startsWith("heading") -> pHeading = 4
                                            lower.contains("list") -> pList = true
                                        }
                                    }
                                }
                            }
                            "outlineLvl" -> {
                                val lvl = getAttrByLocalName(parser, "val")?.toIntOrNull()
                                if (tableDepth == 0 && inParagraph && lvl != null && pHeading == 0) {
                                    pHeading = (lvl + 1).coerceIn(1, 4)
                                }
                            }
                            "numPr" -> {
                                if (tableDepth == 0 && inParagraph) {
                                    pList = true
                                }
                            }
                            "ilvl" -> {
                                if (tableDepth == 0 && inParagraph) {
                                    pListLevel = getAttrByLocalName(parser, "val")?.toIntOrNull()?.coerceIn(0, 4) ?: 0
                                }
                            }
                            "jc" -> {
                                if (tableDepth == 0 && inParagraph) {
                                    val jcVal = getAttrByLocalName(parser, "val")?.lowercase(Locale.US)
                                    if (jcVal == "center" || jcVal == "right") {
                                        pAlign = jcVal
                                    }
                                }
                            }
                            "pageBreakBefore" -> {
                                if (tableDepth == 0 && inParagraph) {
                                    pPageBreak = true
                                }
                            }
                            "r" -> {
                                inRun = true
                                rBold = false
                                rItalic = false
                                rSize = null
                            }
                            "b" -> {
                                val v = getAttrByLocalName(parser, "val")
                                if (v == null || (v != "0" && !v.equals("false", ignoreCase = true))) {
                                    if (inRun) rBold = true else if (inParagraph) pBold = true
                                }
                            }
                            "i" -> {
                                val v = getAttrByLocalName(parser, "val")
                                if (v == null || (v != "0" && !v.equals("false", ignoreCase = true))) {
                                    if (inRun) rItalic = true else if (inParagraph) pItalic = true
                                }
                            }
                            "sz" -> {
                                val halfPts = getAttrByLocalName(parser, "val")?.toFloatOrNull()
                                if (halfPts != null && halfPts > 0f) {
                                    val pt = (halfPts / 2f).coerceIn(8f, 32f)
                                    if (inRun) rSize = pt
                                    if (inParagraph && pFontSize == null) pFontSize = pt
                                }
                            }
                            "t" -> {
                                inText = true
                            }
                            "tab" -> {
                                if (tableDepth == 0 && inParagraph) {
                                    pText.append("    ")
                                } else if (tableDepth == 1) {
                                    currentCellText.append("  ")
                                }
                            }
                            "br" -> {
                                val brType = getAttrByLocalName(parser, "type")
                                if (brType == "page") {
                                    if (tableDepth == 0 && inParagraph) pPageBreak = true
                                } else {
                                    if (tableDepth == 0 && inParagraph) pText.append("\n")
                                    else if (tableDepth == 1) currentCellText.append("\n")
                                }
                            }
                            "blip", "imagedata" -> {
                                val rId = getAttrByLocalName(parser, "embed") ?: getAttrByLocalName(parser, "id")
                                if (rId != null) {
                                    val target = rels[rId]
                                    if (target != null) {
                                        val zipPath = resolveZipPath("word", target)
                                        if (entries.containsKey(zipPath)) {
                                            pImages.add(zipPath)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inText) {
                            val txt = parser.text ?: ""
                            if (tableDepth == 0 && inParagraph) {
                                pText.append(txt)
                                if (txt.isNotBlank()) {
                                    if (rBold) pBold = true else pHasNonBoldRun = true
                                    if (rItalic) pItalic = true
                                    if (rSize != null && pFontSize == null) pFontSize = rSize
                                }
                            } else if (tableDepth == 1) {
                                currentCellText.append(txt)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (localTagName(parser.name)) {
                            "t" -> inText = false
                            "r" -> inRun = false
                            "p" -> {
                                if (tableDepth == 0 && inParagraph) {
                                    inParagraph = false
                                    val textStr = pText.toString().trimEnd()
                                    if (textStr.isNotBlank() || pImages.isNotEmpty() || pPageBreak) {
                                        blocks.add(
                                            DocxBlock.Paragraph(
                                                plainText = textStr,
                                                headingLevel = pHeading,
                                                isBold = pHeading > 0 || (pBold && !pHasNonBoldRun),
                                                isItalic = pItalic,
                                                isListItem = pList,
                                                listLevel = pListLevel,
                                                alignment = pAlign,
                                                fontSizePt = pFontSize,
                                                pageBreakBefore = pPageBreak,
                                                embeddedImagePaths = pImages.toList()
                                            )
                                        )
                                    }
                                }
                            }
                            "tc" -> {
                                if (tableDepth == 1) {
                                    currentRowCells.add(currentCellText.toString().trim())
                                }
                            }
                            "tr" -> {
                                if (tableDepth == 1 && currentRowCells.isNotEmpty()) {
                                    currentTableRows.add(currentRowCells.toList())
                                }
                            }
                            "tbl" -> {
                                if (tableDepth == 1 && currentTableRows.isNotEmpty()) {
                                    blocks.add(DocxBlock.Table(currentTableRows.toList()))
                                }
                                tableDepth = (tableDepth - 1).coerceAtLeast(0)
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }

        return DocxDocumentModel(blocks)
    }

    private fun renderDocxToPdDocument(
        doc: PDDocument,
        model: DocxDocumentModel,
        entries: Map<String, ByteArray>
    ) {
        val pageWidth = PDRectangle.A4.width
        val pageHeight = PDRectangle.A4.height
        val margin = 44f
        val topMargin = 48f
        val bottomMargin = 48f
        val usableWidth = pageWidth - 2 * margin

        var currentPage = PDPage(PDRectangle.A4)
        doc.addPage(currentPage)
        var cs = PDPageContentStream(doc, currentPage)
        var cursorY = pageHeight - topMargin

        fun startNewPage() {
            cs.close()
            currentPage = PDPage(PDRectangle.A4)
            doc.addPage(currentPage)
            cs = PDPageContentStream(doc, currentPage)
            cursorY = pageHeight - topMargin
        }

        for (block in model.blocks) {
            when (block) {
                is DocxBlock.Paragraph -> {
                    if (block.pageBreakBefore && cursorY < pageHeight - topMargin - 10f) {
                        startNewPage()
                    }

                    if (block.plainText.isNotBlank()) {
                        val fontSize = when (block.headingLevel) {
                            9 -> 20f
                            1 -> 16f
                            2 -> 14f
                            3 -> 12.5f
                            4 -> 11.5f
                            else -> block.fontSizePt ?: 11f
                        }
                        val font: PDFont = when {
                            block.isBold && block.isItalic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                            block.isBold || block.headingLevel > 0 -> PDType1Font.HELVETICA_BOLD
                            block.isItalic -> PDType1Font.HELVETICA_OBLIQUE
                            else -> PDType1Font.HELVETICA
                        }
                        val lineHeight = fontSize * 1.42f
                        val indent = if (block.isListItem) (16f + block.listLevel * 14f) else 0f
                        val prefix = if (block.isListItem) "* " else ""
                        val availableWidth = (usableWidth - indent).coerceAtLeast(80f)

                        if (block.headingLevel > 0 && cursorY < pageHeight - topMargin - 8f) {
                            cursorY -= 6f
                        }

                        val lines = wrapTextToLines(prefix + block.plainText, font, fontSize, availableWidth)
                        for ((idx, line) in lines.withIndex()) {
                            if (cursorY - lineHeight < bottomMargin) {
                                startNewPage()
                            }
                            val lineX = when (block.alignment) {
                                "center" -> {
                                    val w = measureTextWidth(font, fontSize, line)
                                    margin + ((usableWidth - w) / 2f).coerceAtLeast(0f)
                                }
                                "right" -> {
                                    val w = measureTextWidth(font, fontSize, line)
                                    (pageWidth - margin - w).coerceAtLeast(margin)
                                }
                                else -> margin + indent + (if (block.isListItem && idx > 0) 10f else 0f)
                            }
                            val colorRgb = if (block.headingLevel > 0) Triple(15, 23, 42) else Triple(30, 30, 35)
                            drawSafeText(
                                cs = cs,
                                font = font,
                                fontSize = fontSize,
                                x = lineX,
                                y = cursorY - fontSize,
                                text = line,
                                r = colorRgb.first,
                                g = colorRgb.second,
                                b = colorRgb.third
                            )
                            cursorY -= lineHeight
                        }
                        cursorY -= if (block.headingLevel > 0) 6f else 5f
                    }

                    for (imgPath in block.embeddedImagePaths) {
                        val imgBytes = entries[imgPath] ?: continue
                        val decoded = decodeEmbeddedImageToPdImage(doc, imgBytes) ?: continue
                        val pdImage = decoded.first
                        val srcW = decoded.second.first.toFloat()
                        val srcH = decoded.second.second.toFloat()

                        val maxImgW = usableWidth * 0.85f
                        val maxImgH = 240f
                        val scale = minOf(maxImgW / srcW, maxImgH / srcH, 1f)
                        val drawW = (srcW * scale).coerceAtLeast(20f)
                        val drawH = (srcH * scale).coerceAtLeast(20f)

                        if (cursorY - drawH - 12f < bottomMargin) {
                            startNewPage()
                        }
                        val drawX = margin + (usableWidth - drawW) / 2f
                        val drawY = cursorY - drawH - 4f
                        cs.drawImage(pdImage, drawX, drawY, drawW, drawH)
                        cursorY = drawY - 10f
                    }
                }

                is DocxBlock.Table -> {
                    val colCount = block.rows.maxOfOrNull { it.size } ?: 0
                    if (colCount <= 0) continue

                    cursorY -= 4f
                    val colWidth = usableWidth / colCount.toFloat()
                    val cellPadding = 5f
                    val cellFontSize = 9.5f
                    val cellLineHeight = 12.5f

                    for ((rowIdx, row) in block.rows.withIndex()) {
                        val isHeaderRow = rowIdx == 0 && block.rows.size > 1
                        val cellFont = if (isHeaderRow) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
                        val wrappedCells = (0 until colCount).map { colIdx ->
                            val cellText = row.getOrNull(colIdx) ?: ""
                            wrapTextToLines(
                                text = cellText,
                                font = cellFont,
                                fontSize = cellFontSize,
                                maxWidth = (colWidth - cellPadding * 2f).coerceAtLeast(24f)
                            ).take(12)
                        }
                        val maxLines = wrappedCells.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                        val rowHeight = maxLines * cellLineHeight + cellPadding * 2f

                        if (cursorY - rowHeight < bottomMargin) {
                            startNewPage()
                        }

                        val rowTop = cursorY
                        val rowBottom = cursorY - rowHeight

                        if (isHeaderRow) {
                            cs.setNonStrokingColor(236, 240, 245)
                            cs.addRect(margin, rowBottom, usableWidth, rowHeight)
                            cs.fill()
                        }

                        cs.setStrokingColor(180, 186, 196)
                        cs.setLineWidth(0.6f)
                        for (colIdx in 0 until colCount) {
                            val cellLeft = margin + colIdx * colWidth
                            cs.addRect(cellLeft, rowBottom, colWidth, rowHeight)
                            cs.stroke()

                            val lines = wrappedCells[colIdx]
                            var textY = rowTop - cellPadding - cellFontSize
                            for (line in lines) {
                                drawSafeText(
                                    cs = cs,
                                    font = cellFont,
                                    fontSize = cellFontSize,
                                    x = cellLeft + cellPadding,
                                    y = textY,
                                    text = line
                                )
                                textY -= cellLineHeight
                            }
                        }

                        cursorY = rowBottom
                    }
                    cursorY -= 10f
                }
            }
        }

        cs.close()
        addPageNumbersFooter(doc)
    }

    // =========================================================================
    // 2. EXCEL (.XLSX) PARSER & PDF RENDERER
    // =========================================================================

    private data class XlsxSheetModel(
        val name: String,
        val rows: List<List<String>>,
        val maxCols: Int
    )

    private fun parseSharedStrings(sharedBytes: ByteArray?): List<String> {
        if (sharedBytes == null) return emptyList()
        val list = mutableListOf<String>()
        try {
            val parser = newXmlPullParser(sharedBytes)
            var event = parser.eventType
            var inSi = false
            var inT = false
            var currentSi = StringBuilder()

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (localTagName(parser.name)) {
                            "si" -> {
                                inSi = true
                                currentSi = StringBuilder()
                            }
                            "t" -> if (inSi) inT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inSi && inT) {
                            currentSi.append(parser.text ?: "")
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (localTagName(parser.name)) {
                            "t" -> inT = false
                            "si" -> {
                                if (inSi) {
                                    list.add(currentSi.toString())
                                    inSi = false
                                }
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun parseXlsxDateStyleIndices(stylesBytes: ByteArray?): Set<Int> {
        if (stylesBytes == null) return emptySet()
        val customDateFmtIds = mutableSetOf<Int>()
        val dateStyleIndices = mutableSetOf<Int>()
        try {
            val parser = newXmlPullParser(stylesBytes)
            var event = parser.eventType
            var inCellXfs = false
            var xfIndex = 0

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (localTagName(parser.name)) {
                            "numFmt" -> {
                                val id = getAttrByLocalName(parser, "numFmtId")?.toIntOrNull()
                                val code = getAttrByLocalName(parser, "formatCode")?.lowercase(Locale.US) ?: ""
                                if (id != null && (code.contains("yy") || code.contains("dd") || code.contains("mm"))) {
                                    customDateFmtIds.add(id)
                                }
                            }
                            "cellXfs" -> {
                                inCellXfs = true
                                xfIndex = 0
                            }
                            "xf" -> {
                                if (inCellXfs) {
                                    val numFmtId = getAttrByLocalName(parser, "numFmtId")?.toIntOrNull() ?: 0
                                    if (numFmtId in 14..22 || numFmtId in 45..47 || customDateFmtIds.contains(numFmtId)) {
                                        dateStyleIndices.add(xfIndex)
                                    }
                                    xfIndex++
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (localTagName(parser.name) == "cellXfs") {
                            inCellXfs = false
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }
        return dateStyleIndices
    }

    private fun excelColRefToIndex(cellRef: String): Int {
        var col = 0
        for (ch in cellRef) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return (col - 1).coerceAtLeast(0)
    }

    private fun indexToExcelColName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun formatExcelNumber(rawValue: String, isDateStyle: Boolean): String {
        val d = rawValue.toDoubleOrNull() ?: return rawValue
        if (isDateStyle && d in 1.0..100000.0) {
            return try {
                val wholeDays = d.toInt()
                val adjustedDays = if (wholeDays > 60) wholeDays - 2 else wholeDays - 1
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    set(1900, Calendar.JANUARY, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, adjustedDays)
                }
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(cal.time)
            } catch (_: Exception) {
                rawValue
            }
        }
        return if (d == d.toLong().toDouble()) {
            d.toLong().toString()
        } else {
            DecimalFormat("#.####").format(d)
        }
    }

    private fun parseXlsxWorkbook(entries: Map<String, ByteArray>): List<XlsxSheetModel> {
        val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"])
        val dateStyleIndices = parseXlsxDateStyleIndices(entries["xl/styles.xml"])
        val rels = parseRelationships(entries["xl/_rels/workbook.xml.rels"])

        val orderedSheets = mutableListOf<Pair<String, String>>()
        val workbookBytes = entries["xl/workbook.xml"]
        if (workbookBytes != null) {
            try {
                val parser = newXmlPullParser(workbookBytes)
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && localTagName(parser.name) == "sheet") {
                        val state = getAttrByLocalName(parser, "state")
                        if (state != "hidden" && state != "veryHidden") {
                            val name = getAttrByLocalName(parser, "name") ?: "Sheet"
                            val rId = getAttrByLocalName(parser, "id")
                            val target = if (rId != null) rels[rId] else null
                            val resolvedPath = if (target != null) {
                                resolveZipPath("xl", target)
                            } else {
                                "xl/worksheets/sheet${orderedSheets.size + 1}.xml"
                            }
                            if (entries.containsKey(resolvedPath)) {
                                orderedSheets.add(name to resolvedPath)
                            }
                        }
                    }
                    event = parser.next()
                }
            } catch (_: Exception) {
            }
        }

        if (orderedSheets.isEmpty()) {
            entries.keys
                .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sorted()
                .forEachIndexed { idx, path ->
                    orderedSheets.add("Sheet ${idx + 1}" to path)
                }
        }

        return orderedSheets.map { (sheetName, path) ->
            val sheetBytes = entries[path]
            if (sheetBytes == null) {
                XlsxSheetModel(sheetName, emptyList(), 0)
            } else {
                parseXlsxWorksheet(sheetName, sheetBytes, sharedStrings, dateStyleIndices)
            }
        }
    }

    private fun parseXlsxWorksheet(
        sheetName: String,
        sheetBytes: ByteArray,
        sharedStrings: List<String>,
        dateStyleIndices: Set<Int>
    ): XlsxSheetModel {
        val rows = mutableListOf<List<String>>()
        var maxCols = 0
        try {
            val parser = newXmlPullParser(sheetBytes)
            var event = parser.eventType

            var inRow = false
            var currentRowMap = sortedMapOf<Int, String>()
            var nextFallbackCol = 0

            var inCell = false
            var cellColIdx = 0
            var cellType = ""
            var cellStyleIdx = -1
            var cellValue = StringBuilder()
            var cellFormula = StringBuilder()
            var cellInlineText = StringBuilder()
            var inV = false
            var inF = false
            var inIsT = false

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (localTagName(parser.name)) {
                            "row" -> {
                                inRow = true
                                currentRowMap = sortedMapOf()
                                nextFallbackCol = 0
                            }
                            "c" -> {
                                if (inRow) {
                                    inCell = true
                                    val ref = getAttrByLocalName(parser, "r")
                                    cellColIdx = if (!ref.isNullOrBlank()) excelColRefToIndex(ref) else nextFallbackCol
                                    nextFallbackCol = cellColIdx + 1
                                    cellType = getAttrByLocalName(parser, "t") ?: ""
                                    cellStyleIdx = getAttrByLocalName(parser, "s")?.toIntOrNull() ?: -1
                                    cellValue = StringBuilder()
                                    cellFormula = StringBuilder()
                                    cellInlineText = StringBuilder()
                                }
                            }
                            "v" -> if (inCell) inV = true
                            "f" -> if (inCell) inF = true
                            "t" -> if (inCell) inIsT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val txt = parser.text ?: ""
                        when {
                            inV -> cellValue.append(txt)
                            inF -> cellFormula.append(txt)
                            inIsT -> cellInlineText.append(txt)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (localTagName(parser.name)) {
                            "v" -> inV = false
                            "f" -> inF = false
                            "t" -> inIsT = false
                            "c" -> {
                                if (inCell) {
                                    inCell = false
                                    val rawVal = cellValue.toString().trim()
                                    val resolved = when (cellType) {
                                        "s" -> {
                                            val idx = rawVal.toIntOrNull()
                                            if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else rawVal
                                        }
                                        "inlineStr" -> cellInlineText.toString()
                                        "b" -> if (rawVal == "1" || rawVal.equals("true", ignoreCase = true)) "TRUE" else "FALSE"
                                        "str", "e" -> rawVal.ifEmpty { cellInlineText.toString() }
                                        else -> {
                                            if (rawVal.isNotEmpty()) {
                                                formatExcelNumber(rawVal, dateStyleIndices.contains(cellStyleIdx))
                                            } else if (cellInlineText.isNotEmpty()) {
                                                cellInlineText.toString()
                                            } else if (cellFormula.isNotEmpty()) {
                                                "=${cellFormula}"
                                            } else {
                                                ""
                                            }
                                        }
                                    }
                                    if (resolved.isNotBlank()) {
                                        currentRowMap[cellColIdx] = resolved
                                    }
                                }
                            }
                            "row" -> {
                                if (inRow) {
                                    inRow = false
                                    if (currentRowMap.isNotEmpty()) {
                                        val lastCol = (currentRowMap.lastKey() + 1).coerceAtMost(32)
                                        if (lastCol > maxCols) maxCols = lastCol
                                        val rowList = (0 until lastCol).map { currentRowMap[it] ?: "" }
                                        rows.add(rowList)
                                    }
                                }
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }
        return XlsxSheetModel(sheetName, rows, maxCols)
    }

    private fun renderXlsxToPdDocument(
        doc: PDDocument,
        sheets: List<XlsxSheetModel>
    ) {
        for (sheet in sheets) {
            val totalCols = sheet.maxCols.coerceAtLeast(1)
            val isLandscape = totalCols > 5
            val pageRect = if (isLandscape) {
                PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
            } else {
                PDRectangle.A4
            }
            val pageWidth = pageRect.width
            val pageHeight = pageRect.height
            val margin = 36f
            val topMargin = 42f
            val bottomMargin = 42f
            val usableWidth = pageWidth - 2 * margin

            val maxColsPerBand = if (isLandscape) 10 else 6
            val colBands = (0 until totalCols).chunked(maxColsPerBand)

            for ((bandIdx, colIndices) in colBands.withIndex()) {
                var page = PDPage(pageRect)
                doc.addPage(page)
                var cs = PDPageContentStream(doc, page)
                var cursorY = pageHeight - topMargin

                fun drawSheetHeader(isContinuation: Boolean) {
                    val bandLabel = if (colBands.size > 1) {
                        " (Columns ${indexToExcelColName(colIndices.first())}–${indexToExcelColName(colIndices.last())})"
                    } else {
                        ""
                    }
                    val contLabel = if (isContinuation) " (Continued)" else ""
                    drawSafeText(
                        cs = cs,
                        font = PDType1Font.HELVETICA_BOLD,
                        fontSize = 14f,
                        x = margin,
                        y = cursorY - 14f,
                        text = "Worksheet: ${sheet.name}$bandLabel$contLabel",
                        r = 15,
                        g = 23,
                        b = 42
                    )
                    cursorY -= 22f
                }

                drawSheetHeader(isContinuation = false)

                val rowNumColWidth = 28f
                val tableUsableWidth = usableWidth - rowNumColWidth
                val colWidth = tableUsableWidth / colIndices.size.toFloat()
                val cellPadding = 4f
                val fontSize = 8.5f
                val lineHeight = 11f

                fun drawColumnLetterBanner() {
                    val bannerHeight = 18f
                    val bottom = cursorY - bannerHeight
                    cs.setNonStrokingColor(226, 232, 240)
                    cs.addRect(margin, bottom, usableWidth, bannerHeight)
                    cs.fill()

                    cs.setStrokingColor(160, 170, 185)
                    cs.setLineWidth(0.5f)
                    cs.addRect(margin, bottom, rowNumColWidth, bannerHeight)
                    cs.stroke()
                    drawSafeText(
                        cs = cs,
                        font = PDType1Font.HELVETICA_BOLD,
                        fontSize = 8f,
                        x = margin + 4f,
                        y = bottom + 5f,
                        text = "#"
                    )

                    for ((localIdx, colIdx) in colIndices.withIndex()) {
                        val left = margin + rowNumColWidth + localIdx * colWidth
                        cs.addRect(left, bottom, colWidth, bannerHeight)
                        cs.stroke()
                        val colLetter = indexToExcelColName(colIdx)
                        drawSafeText(
                            cs = cs,
                            font = PDType1Font.HELVETICA_BOLD,
                            fontSize = 8.5f,
                            x = left + cellPadding,
                            y = bottom + 5f,
                            text = colLetter
                        )
                    }
                    cursorY = bottom
                }

                drawColumnLetterBanner()

                for ((rowIdx, row) in sheet.rows.withIndex()) {
                    val isFirstRow = rowIdx == 0
                    val font = if (isFirstRow) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
                    val wrappedCells = colIndices.map { colIdx ->
                        val cellText = row.getOrNull(colIdx) ?: ""
                        wrapTextToLines(
                            text = cellText,
                            font = font,
                            fontSize = fontSize,
                            maxWidth = (colWidth - cellPadding * 2f).coerceAtLeast(20f)
                        ).take(8)
                    }
                    val maxLines = wrappedCells.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                    val rowHeight = maxLines * lineHeight + cellPadding * 2f

                    if (cursorY - rowHeight < bottomMargin) {
                        cs.close()
                        page = PDPage(pageRect)
                        doc.addPage(page)
                        cs = PDPageContentStream(doc, page)
                        cursorY = pageHeight - topMargin
                        drawSheetHeader(isContinuation = true)
                        drawColumnLetterBanner()
                    }

                    val rowTop = cursorY
                    val rowBottom = cursorY - rowHeight

                    if (isFirstRow) {
                        cs.setNonStrokingColor(245, 247, 250)
                        cs.addRect(margin + rowNumColWidth, rowBottom, tableUsableWidth, rowHeight)
                        cs.fill()
                    }

                    // Row number cell
                    cs.setNonStrokingColor(248, 250, 252)
                    cs.addRect(margin, rowBottom, rowNumColWidth, rowHeight)
                    cs.fill()
                    cs.setStrokingColor(190, 198, 210)
                    cs.setLineWidth(0.5f)
                    cs.addRect(margin, rowBottom, rowNumColWidth, rowHeight)
                    cs.stroke()
                    drawSafeText(
                        cs = cs,
                        font = PDType1Font.HELVETICA,
                        fontSize = 7.5f,
                        x = margin + 4f,
                        y = rowTop - cellPadding - 7.5f,
                        text = (rowIdx + 1).toString(),
                        r = 100,
                        g = 110,
                        b = 125
                    )

                    for ((localIdx, lines) in wrappedCells.withIndex()) {
                        val cellLeft = margin + rowNumColWidth + localIdx * colWidth
                        cs.setStrokingColor(190, 198, 210)
                        cs.addRect(cellLeft, rowBottom, colWidth, rowHeight)
                        cs.stroke()

                        var textY = rowTop - cellPadding - fontSize
                        for (line in lines) {
                            drawSafeText(
                                cs = cs,
                                font = font,
                                fontSize = fontSize,
                                x = cellLeft + cellPadding,
                                y = textY,
                                text = line
                            )
                            textY -= lineHeight
                        }
                    }

                    cursorY = rowBottom
                }

                cs.close()
            }
        }
        addPageNumbersFooter(doc)
    }

    // =========================================================================
    // 3. POWERPOINT (.PPTX) PARSER & PDF RENDERER
    // =========================================================================

    private data class PptxBulletLine(
        val text: String,
        val level: Int,
        val isBold: Boolean
    )

    private data class PptxSlideModel(
        val slideNumber: Int,
        val title: String,
        val subtitle: String,
        val bulletLines: List<PptxBulletLine>,
        val tables: List<List<List<String>>>,
        val imagePaths: List<String>,
        val speakerNotes: String
    ) {
        fun isEmpty(): Boolean =
            title.isBlank() &&
                subtitle.isBlank() &&
                bulletLines.isEmpty() &&
                tables.isEmpty() &&
                imagePaths.isEmpty() &&
                speakerNotes.isBlank()
    }

    private fun parsePptxPresentation(entries: Map<String, ByteArray>): List<PptxSlideModel> {
        val presRels = parseRelationships(entries["ppt/_rels/presentation.xml.rels"])
        val orderedSlidePaths = mutableListOf<String>()

        val presBytes = entries["ppt/presentation.xml"]
        if (presBytes != null) {
            try {
                val parser = newXmlPullParser(presBytes)
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && localTagName(parser.name) == "sldId") {
                        val rId = getAttrByLocalName(parser, "id")
                        val target = if (rId != null) presRels[rId] else null
                        if (target != null) {
                            val resolved = resolveZipPath("ppt", target)
                            if (entries.containsKey(resolved)) {
                                orderedSlidePaths.add(resolved)
                            }
                        }
                    }
                    event = parser.next()
                }
            } catch (_: Exception) {
            }
        }

        if (orderedSlidePaths.isEmpty()) {
            entries.keys
                .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
                .sortedWith(compareBy { path ->
                    Regex("slide(\\d+)\\.xml").find(path)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
                })
                .forEach { orderedSlidePaths.add(it) }
        }

        return orderedSlidePaths.mapIndexed { idx, slidePath ->
            val slideFileName = slidePath.substringAfterLast('/')
            val relsPath = "ppt/slides/_rels/$slideFileName.rels"
            val slideRels = parseRelationships(entries[relsPath])
            val slideBytes = entries[slidePath]
            if (slideBytes == null) {
                PptxSlideModel(idx + 1, "", "", emptyList(), emptyList(), emptyList(), "")
            } else {
                parseSinglePptxSlide(idx + 1, slideBytes, slideRels, entries)
            }
        }
    }

    private fun parseSinglePptxSlide(
        slideNumber: Int,
        slideBytes: ByteArray,
        slideRels: Map<String, String>,
        entries: Map<String, ByteArray>
    ): PptxSlideModel {
        var title = ""
        var subtitle = ""
        val bullets = mutableListOf<PptxBulletLine>()
        val tables = mutableListOf<List<List<String>>>()
        val images = mutableListOf<String>()

        try {
            val parser = newXmlPullParser(slideBytes)
            var event = parser.eventType

            var inSp = false
            var currentPhType = ""
            var inTxBody = false
            var inAParagraph = false
            var aParaText = StringBuilder()
            var aParaLevel = 0
            var aParaBold = false
            var inAText = false

            var inTable = false
            var currentTable = mutableListOf<List<String>>()
            var currentRow = mutableListOf<String>()
            var currentCell = StringBuilder()

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (localTagName(parser.name)) {
                            "sp" -> {
                                inSp = true
                                currentPhType = ""
                            }
                            "ph" -> {
                                if (inSp) {
                                    currentPhType = getAttrByLocalName(parser, "type")?.lowercase(Locale.US) ?: "body"
                                }
                            }
                            "txBody" -> if (inSp) inTxBody = true
                            "tbl" -> {
                                inTable = true
                                currentTable = mutableListOf()
                            }
                            "tr" -> if (inTable) currentRow = mutableListOf()
                            "tc" -> if (inTable) currentCell = StringBuilder()
                            "p" -> {
                                if (inTable) {
                                    if (currentCell.isNotEmpty()) currentCell.append(" ")
                                } else if (inTxBody) {
                                    inAParagraph = true
                                    aParaText = StringBuilder()
                                    aParaLevel = 0
                                    aParaBold = false
                                }
                            }
                            "pPr" -> {
                                if (inAParagraph) {
                                    aParaLevel = getAttrByLocalName(parser, "lvl")?.toIntOrNull()?.coerceIn(0, 4) ?: 0
                                }
                            }
                            "rPr" -> {
                                if (inAParagraph) {
                                    val bAttr = getAttrByLocalName(parser, "b")
                                    if (bAttr == "1" || bAttr.equals("true", ignoreCase = true)) {
                                        aParaBold = true
                                    }
                                }
                            }
                            "t" -> inAText = true
                            "br" -> {
                                if (inTable) currentCell.append("\n")
                                else if (inAParagraph) aParaText.append("\n")
                            }
                            "blip" -> {
                                val rId = getAttrByLocalName(parser, "embed")
                                if (rId != null) {
                                    val target = slideRels[rId]
                                    if (target != null) {
                                        val mediaPath = resolveZipPath("ppt/slides", target)
                                        if (entries.containsKey(mediaPath)) {
                                            images.add(mediaPath)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inAText) {
                            val txt = parser.text ?: ""
                            if (inTable) {
                                currentCell.append(txt)
                            } else if (inAParagraph) {
                                aParaText.append(txt)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (localTagName(parser.name)) {
                            "t" -> inAText = false
                            "p" -> {
                                if (!inTable && inAParagraph) {
                                    inAParagraph = false
                                    val clean = aParaText.toString().trim()
                                    if (clean.isNotBlank()) {
                                        when {
                                            (currentPhType == "title" || currentPhType == "ctrtitle") && title.isBlank() -> {
                                                title = clean
                                            }
                                            currentPhType == "subtitle" && subtitle.isBlank() -> {
                                                subtitle = clean
                                            }
                                            else -> {
                                                bullets.add(PptxBulletLine(clean, aParaLevel, aParaBold))
                                            }
                                        }
                                    }
                                }
                            }
                            "txBody" -> inTxBody = false
                            "sp" -> inSp = false
                            "tc" -> if (inTable) currentRow.add(currentCell.toString().trim())
                            "tr" -> if (inTable && currentRow.isNotEmpty()) currentTable.add(currentRow.toList())
                            "tbl" -> {
                                if (inTable && currentTable.isNotEmpty()) {
                                    tables.add(currentTable.toList())
                                }
                                inTable = false
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }

        // If no explicit title placeholder was marked, promote first prominent short line if appropriate
        if (title.isBlank() && bullets.isNotEmpty() && bullets.first().level == 0 && bullets.first().text.length <= 90) {
            title = bullets.removeAt(0).text
        }

        // Parse speaker notes if linked
        var notesText = ""
        val notesTarget = slideRels.values.firstOrNull { it.contains("notesSlide") }
        if (notesTarget != null) {
            val notesPath = resolveZipPath("ppt/slides", notesTarget)
            val notesBytes = entries[notesPath]
            if (notesBytes != null) {
                notesText = parseSpeakerNotes(notesBytes)
            }
        }

        return PptxSlideModel(
            slideNumber = slideNumber,
            title = title,
            subtitle = subtitle,
            bulletLines = bullets,
            tables = tables,
            imagePaths = images.distinct(),
            speakerNotes = notesText
        )
    }

    private fun parseSpeakerNotes(notesBytes: ByteArray): String {
        val lines = mutableListOf<String>()
        try {
            val parser = newXmlPullParser(notesBytes)
            var event = parser.eventType
            var inSp = false
            var phType = ""
            var inP = false
            var pBuf = StringBuilder()
            var inT = false

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (localTagName(parser.name)) {
                            "sp" -> {
                                inSp = true
                                phType = ""
                            }
                            "ph" -> if (inSp) phType = getAttrByLocalName(parser, "type") ?: ""
                            "p" -> if (inSp) {
                                inP = true
                                pBuf = StringBuilder()
                            }
                            "t" -> if (inP) inT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) pBuf.append(parser.text ?: "")
                    }
                    XmlPullParser.END_TAG -> {
                        when (localTagName(parser.name)) {
                            "t" -> inT = false
                            "p" -> {
                                if (inP) {
                                    inP = false
                                    val txt = pBuf.toString().trim()
                                    if (txt.isNotBlank() && phType != "sldNum") {
                                        lines.add(txt)
                                    }
                                }
                            }
                            "sp" -> inSp = false
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }
        return lines.joinToString(" ")
    }

    private fun renderPptxToPdDocument(
        doc: PDDocument,
        slides: List<PptxSlideModel>,
        entries: Map<String, ByteArray>
    ) {
        val slideWidth = 842f
        val slideHeight = 474f // 16:9 widescreen slide in points
        val slideRect = PDRectangle(slideWidth, slideHeight)
        val margin = 42f
        val usableWidth = slideWidth - 2 * margin
        val bottomMargin = 38f

        val totalSlides = slides.size

        for (slide in slides) {
            var page = PDPage(slideRect)
            doc.addPage(page)
            var cs = PDPageContentStream(doc, page)

            fun drawSlideChrome(isContinuation: Boolean) {
                // Subtle top header bar
                cs.setNonStrokingColor(18, 24, 38)
                cs.addRect(0f, slideHeight - 10f, slideWidth, 10f)
                cs.fill()

                // Slide border frame
                cs.setStrokingColor(215, 220, 228)
                cs.setLineWidth(1f)
                cs.addRect(12f, 12f, slideWidth - 24f, slideHeight - 24f)
                cs.stroke()

                val badge = if (isContinuation) {
                    "Slide ${slide.slideNumber} / $totalSlides (Cont.)"
                } else {
                    "Slide ${slide.slideNumber} / $totalSlides"
                }
                val badgeW = measureTextWidth(PDType1Font.HELVETICA_BOLD, 9f, badge)
                drawSafeText(
                    cs = cs,
                    font = PDType1Font.HELVETICA_BOLD,
                    fontSize = 9f,
                    x = slideWidth - margin - badgeW,
                    y = 22f,
                    text = badge,
                    r = 110,
                    g = 118,
                    b = 132
                )
            }

            drawSlideChrome(isContinuation = false)
            var cursorY = slideHeight - 44f

            // Slide Title
            val displayTitle = slide.title.ifBlank { "Slide ${slide.slideNumber}" }
            val titleLines = wrapTextToLines(displayTitle, PDType1Font.HELVETICA_BOLD, 22f, usableWidth)
            for (line in titleLines) {
                drawSafeText(
                    cs = cs,
                    font = PDType1Font.HELVETICA_BOLD,
                    fontSize = 22f,
                    x = margin,
                    y = cursorY - 22f,
                    text = line,
                    r = 15,
                    g = 23,
                    b = 42
                )
                cursorY -= 29f
            }

            // Subtitle
            if (slide.subtitle.isNotBlank()) {
                val subLines = wrapTextToLines(slide.subtitle, PDType1Font.HELVETICA_OBLIQUE, 14f, usableWidth)
                for (line in subLines) {
                    drawSafeText(
                        cs = cs,
                        font = PDType1Font.HELVETICA_OBLIQUE,
                        fontSize = 14f,
                        x = margin,
                        y = cursorY - 14f,
                        text = line,
                        r = 75,
                        g = 85,
                        b = 99
                    )
                    cursorY -= 19f
                }
            }

            // Divider rule under title block
            cursorY -= 4f
            cs.setStrokingColor(220, 226, 235)
            cs.setLineWidth(0.8f)
            cs.moveTo(margin, cursorY)
            cs.lineTo(slideWidth - margin, cursorY)
            cs.stroke()
            cursorY -= 16f

            fun startContinuationSlide() {
                cs.close()
                page = PDPage(slideRect)
                doc.addPage(page)
                cs = PDPageContentStream(doc, page)
                drawSlideChrome(isContinuation = true)
                cursorY = slideHeight - 44f
                drawSafeText(
                    cs = cs,
                    font = PDType1Font.HELVETICA_BOLD,
                    fontSize = 15f,
                    x = margin,
                    y = cursorY - 15f,
                    text = "$displayTitle (Continued)",
                    r = 15,
                    g = 23,
                    b = 42
                )
                cursorY -= 28f
            }

            // Bullet points
            for (bullet in slide.bulletLines) {
                val indent = bullet.level * 20f
                val fontSize = if (bullet.level == 0) 13.5f else 12f
                val lineHeight = fontSize * 1.4f
                val font = if (bullet.isBold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
                val prefix = if (bullet.level == 0) "* " else "- "
                val wrapped = wrapTextToLines(prefix + bullet.text, font, fontSize, (usableWidth - indent).coerceAtLeast(120f))

                for ((lineIdx, line) in wrapped.withIndex()) {
                    if (cursorY - lineHeight < bottomMargin) {
                        startContinuationSlide()
                    }
                    val xOffset = margin + indent + (if (lineIdx > 0) 12f else 0f)
                    drawSafeText(
                        cs = cs,
                        font = font,
                        fontSize = fontSize,
                        x = xOffset,
                        y = cursorY - fontSize,
                        text = line,
                        r = 30,
                        g = 35,
                        b = 45
                    )
                    cursorY -= lineHeight
                }
                cursorY -= 4f
            }

            // Slide Tables
            for (table in slide.tables) {
                val colCount = table.maxOfOrNull { it.size } ?: 0
                if (colCount <= 0) continue
                val colWidth = usableWidth / colCount.toFloat()
                val cellPadding = 5f
                val cellFontSize = 10.5f
                val cellLineHeight = 13.5f

                for ((rIdx, row) in table.withIndex()) {
                    val isHeader = rIdx == 0 && table.size > 1
                    val font = if (isHeader) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
                    val wrappedCells = (0 until colCount).map { cIdx ->
                        wrapTextToLines(
                            text = row.getOrNull(cIdx) ?: "",
                            font = font,
                            fontSize = cellFontSize,
                            maxWidth = (colWidth - cellPadding * 2f).coerceAtLeast(24f)
                        ).take(6)
                    }
                    val maxLines = wrappedCells.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                    val rowH = maxLines * cellLineHeight + cellPadding * 2f

                    if (cursorY - rowH < bottomMargin) {
                        startContinuationSlide()
                    }

                    val rowTop = cursorY
                    val rowBottom = cursorY - rowH
                    if (isHeader) {
                        cs.setNonStrokingColor(235, 240, 248)
                        cs.addRect(margin, rowBottom, usableWidth, rowH)
                        cs.fill()
                    }
                    cs.setStrokingColor(180, 188, 200)
                    cs.setLineWidth(0.6f)
                    for (cIdx in 0 until colCount) {
                        val left = margin + cIdx * colWidth
                        cs.addRect(left, rowBottom, colWidth, rowH)
                        cs.stroke()
                        var ty = rowTop - cellPadding - cellFontSize
                        for (line in wrappedCells[cIdx]) {
                            drawSafeText(cs, font, cellFontSize, left + cellPadding, ty, line)
                            ty -= cellLineHeight
                        }
                    }
                    cursorY = rowBottom
                }
                cursorY -= 8f
            }

            // Embedded Slide Images
            for (imgPath in slide.imagePaths) {
                val imgBytes = entries[imgPath] ?: continue
                val decoded = decodeEmbeddedImageToPdImage(doc, imgBytes) ?: continue
                val pdImg = decoded.first
                val srcW = decoded.second.first.toFloat()
                val srcH = decoded.second.second.toFloat()

                val maxW = usableWidth * 0.65f
                val maxH = 180f
                val scale = minOf(maxW / srcW, maxH / srcH, 1f)
                val drawW = (srcW * scale).coerceAtLeast(24f)
                val drawH = (srcH * scale).coerceAtLeast(24f)

                if (cursorY - drawH - 8f < bottomMargin) {
                    startContinuationSlide()
                }
                val drawX = margin + (usableWidth - drawW) / 2f
                val drawY = cursorY - drawH - 4f
                cs.drawImage(pdImg, drawX, drawY, drawW, drawH)
                cursorY = drawY - 8f
            }

            // Speaker Notes
            if (slide.speakerNotes.isNotBlank()) {
                val noteLines = wrapTextToLines(
                    text = "Notes: ${slide.speakerNotes}",
                    font = PDType1Font.HELVETICA_OBLIQUE,
                    fontSize = 9.5f,
                    maxWidth = usableWidth
                ).take(4)
                val notesHeight = noteLines.size * 12.5f + 6f
                if (cursorY - notesHeight < bottomMargin) {
                    startContinuationSlide()
                }
                cursorY -= 4f
                for (line in noteLines) {
                    drawSafeText(
                        cs = cs,
                        font = PDType1Font.HELVETICA_OBLIQUE,
                        fontSize = 9.5f,
                        x = margin,
                        y = cursorY - 9.5f,
                        text = line,
                        r = 95,
                        g = 105,
                        b = 120
                    )
                    cursorY -= 12.5f
                }
            }

            cs.close()
        }
    }

    private fun addPageNumbersFooter(doc: PDDocument) {
        val total = doc.numberOfPages
        if (total <= 0) return
        for (i in 0 until total) {
            val page = doc.getPage(i)
            val width = page.mediaBox.width
            val label = "Page ${i + 1} of $total"
            val textW = measureTextWidth(PDType1Font.HELVETICA, 8.5f, label)
            try {
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    drawSafeText(
                        cs = cs,
                        font = PDType1Font.HELVETICA,
                        fontSize = 8.5f,
                        x = (width - textW) / 2f,
                        y = 18f,
                        text = label,
                        r = 120,
                        g = 125,
                        b = 135
                    )
                }
            } catch (_: Exception) {
            }
        }
    }
}
