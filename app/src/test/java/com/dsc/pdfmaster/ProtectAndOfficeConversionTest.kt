package com.dsc.pdfmaster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.dsc.pdfmaster.pdfengine.OfficeToPdfConverter
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProtectAndOfficeConversionTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        File(context.filesDir, "output_pdfs").deleteRecursively()
        File(context.filesDir, "managed_pdfs").deleteRecursively()
    }

    private fun createValidUnencryptedPdf(file: File, pageCount: Int = 2, textPrefix: String = "Confidential Page"): File {
        file.parentFile?.mkdirs()
        PDDocument().use { doc ->
            for (i in 1..pageCount) {
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 14f)
                    cs.newLineAtOffset(50f, 750f)
                    cs.showText("$textPrefix $i")
                    cs.endText()
                }
            }
            doc.save(file)
        }
        return file
    }

    private fun createSamplePngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(40, 80, 160))
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    // =========================================================================
    // TASK 1 TESTS — Protect PDF
    // =========================================================================

    @Test
    fun `protectPdf screen does not select or process starter sample PDF when opened directly`() = runBlocking {
        val viewModel = PdfMasterViewModel(ApplicationProvider.getApplicationContext())
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Navigate directly to PASSWORD_PROTECT from HOME without an explicit user selection
        viewModel.navigateTo(ScreenRoute.PASSWORD_PROTECT)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertFalse("Sample PDF must not be marked as explicitly selected", viewModel.isPdfExplicitlySelected.value)
        assertNull("Protect source PDF must be null when no document was explicitly selected", viewModel.protectSourcePdf.value)

        // Attempting to execute protection without explicit selection must fail safely
        viewModel.executeProtectPdf(
            file = null,
            password = "SecretPassword123",
            confirmPassword = "SecretPassword123"
        )
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertNull("No protected result should be produced without explicit selection", viewModel.protectResult.value)
        assertNotNull("An error message should be shown requiring PDF selection", viewModel.protectPdfErrorMessage.value)
    }

    @Test
    fun `opening PDF in Reader and navigating to Protect PDF selects that explicitly opened document`() = runBlocking {
        val viewModel = PdfMasterViewModel(ApplicationProvider.getApplicationContext())
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val managedDir = File(context.filesDir, "managed_pdfs").apply { mkdirs() }
        val userPdf = createValidUnencryptedPdf(File(managedDir, "contract.pdf"), pageCount = 3)

        viewModel.openPdfInViewer(userPdf)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(ScreenRoute.PDF_VIEWER, viewModel.currentRoute.value)
        assertTrue(viewModel.isPdfExplicitlySelected.value)

        viewModel.navigateTo(ScreenRoute.PASSWORD_PROTECT)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(userPdf.absolutePath, viewModel.protectSourcePdf.value?.absolutePath)
    }

    @Test
    fun `protectPdfDetailed rejects empty, whitespace, and mismatched confirmation passwords`() = runBlocking {
        val source = createValidUnencryptedPdf(File(context.cacheDir, "input_doc.pdf"), pageCount = 1)

        val emptyResult = PdfEngine.protectPdfDetailed(
            context = context,
            pdfFile = source,
            userPassword = "",
            confirmPassword = "",
            outputName = "should_not_exist.pdf"
        )
        assertTrue(emptyResult is PdfEngine.ProtectResult.Error)
        assertEquals(
            PdfEngine.ProtectError.EMPTY_OR_BLANK_PASSWORD,
            (emptyResult as PdfEngine.ProtectResult.Error).error
        )

        val blankResult = PdfEngine.protectPdfDetailed(
            context = context,
            pdfFile = source,
            userPassword = "   ",
            confirmPassword = "   ",
            outputName = "should_not_exist_blank.pdf"
        )
        assertTrue(blankResult is PdfEngine.ProtectResult.Error)
        assertEquals(
            PdfEngine.ProtectError.EMPTY_OR_BLANK_PASSWORD,
            (blankResult as PdfEngine.ProtectResult.Error).error
        )

        val mismatchResult = PdfEngine.protectPdfDetailed(
            context = context,
            pdfFile = source,
            userPassword = "CorrectHorseBatteryStaple",
            confirmPassword = "DifferentPassword",
            outputName = "should_not_exist_mismatch.pdf"
        )
        assertTrue(mismatchResult is PdfEngine.ProtectResult.Error)
        assertEquals(
            PdfEngine.ProtectError.PASSWORD_CONFIRMATION_MISMATCH,
            (mismatchResult as PdfEngine.ProtectResult.Error).error
        )
    }

    @Test
    fun `protectPdfDetailed encrypts PDF, verifies password requirement, preserves pages, and keeps original untouched`() = runBlocking {
        val sourceFile = createValidUnencryptedPdf(
            File(context.cacheDir, "annual_report.pdf"),
            pageCount = 3,
            textPrefix = "Annual Financial Report"
        )
        val password = "StrongPassword#2026"

        val result = PdfEngine.protectPdfDetailed(
            context = context,
            pdfFile = sourceFile,
            userPassword = password,
            confirmPassword = password,
            allowPrinting = false,
            allowCopy = false,
            outputName = "annual_report_protected.pdf",
            inPlace = false
        )

        assertTrue("Expected ProtectResult.Success but got $result", result is PdfEngine.ProtectResult.Success)
        val success = result as PdfEngine.ProtectResult.Success
        assertEquals(3, success.pageCount)
        assertTrue(success.file.exists())
        assertFalse(success.replacedOriginal)
        assertEquals("annual_report_protected.pdf", success.file.name)

        // 1. Original file must still open without a password
        PDDocument.load(sourceFile).use { origDoc ->
            assertFalse("Original PDF must remain unencrypted", origDoc.isEncrypted)
            assertEquals(3, origDoc.numberOfPages)
        }

        // 2. Protected file must fail to open without a password
        var failedWithoutPassword = false
        try {
            PDDocument.load(success.file).use { }
        } catch (e: InvalidPasswordException) {
            failedWithoutPassword = true
        }
        assertTrue("Protected PDF must reject opening without a password", failedWithoutPassword)

        // 3. Protected file must fail to open with wrong password
        var failedWithWrongPassword = false
        try {
            PDDocument.load(success.file, "WrongPassword!").use { }
        } catch (e: InvalidPasswordException) {
            failedWithWrongPassword = true
        }
        assertTrue("Protected PDF must reject opening with wrong password", failedWithWrongPassword)

        // 4. Protected file must open with the correct password and preserve all pages & text
        PDDocument.load(success.file, password).use { encDoc ->
            assertTrue(encDoc.isEncrypted)
            assertEquals(3, encDoc.numberOfPages)
            val extracted = PDFTextStripper().getText(encDoc)
            assertTrue(extracted.contains("Annual Financial Report 1"))
            assertTrue(extracted.contains("Annual Financial Report 3"))
        }

        // 5. Protecting again with same output name generates unique filename
        val secondResult = PdfEngine.protectPdfDetailed(
            context = context,
            pdfFile = sourceFile,
            userPassword = password,
            outputName = "annual_report_protected.pdf",
            inPlace = false
        )
        assertTrue(secondResult is PdfEngine.ProtectResult.Success)
        val secondSuccess = secondResult as PdfEngine.ProtectResult.Success
        assertEquals("annual_report_protected (1).pdf", secondSuccess.file.name)
    }

    @Test
    fun `protectPdfDetailed handles already-encrypted PDFs and supports round-trip unprotect`() = runBlocking {
        val source = createValidUnencryptedPdf(File(context.cacheDir, "secret.pdf"), pageCount = 2)
        val firstPass = "FirstPass123"
        val secondPass = "SecondPass456"

        val firstProtect = PdfEngine.protectPdfDetailed(
            context = context,
            pdfFile = source,
            userPassword = firstPass,
            outputName = "secret_protected.pdf"
        ) as PdfEngine.ProtectResult.Success

        // Attempting to protect an already-encrypted PDF without currentPassword must return ALREADY_ENCRYPTED
        val noCurrentPassAttempt = PdfEngine.protectPdfDetailed(
            context = context,
            pdfFile = firstProtect.file,
            userPassword = secondPass,
            currentPassword = null,
            outputName = "secret_reprotected.pdf"
        )
        assertTrue(noCurrentPassAttempt is PdfEngine.ProtectResult.Error)
        assertEquals(
            PdfEngine.ProtectError.ALREADY_ENCRYPTED,
            (noCurrentPassAttempt as PdfEngine.ProtectResult.Error).error
        )

        // Attempting with wrong currentPassword must return INCORRECT_CURRENT_PASSWORD
        val wrongCurrentPassAttempt = PdfEngine.protectPdfDetailed(
            context = context,
            pdfFile = firstProtect.file,
            userPassword = secondPass,
            currentPassword = "WrongCurrentPassword",
            outputName = "secret_reprotected.pdf"
        )
        assertTrue(wrongCurrentPassAttempt is PdfEngine.ProtectResult.Error)
        assertEquals(
            PdfEngine.ProtectError.INCORRECT_CURRENT_PASSWORD,
            (wrongCurrentPassAttempt as PdfEngine.ProtectResult.Error).error
        )

        // Re-protecting with valid currentPassword must succeed and require secondPass
        val reProtect = PdfEngine.protectPdfDetailed(
            context = context,
            pdfFile = firstProtect.file,
            userPassword = secondPass,
            currentPassword = firstPass,
            outputName = "secret_reprotected.pdf"
        )
        assertTrue(reProtect is PdfEngine.ProtectResult.Success)
        val reProtectedFile = (reProtect as PdfEngine.ProtectResult.Success).file

        // Round-trip: remove password using removePasswordDetailed
        val unprotect = PdfEngine.removePasswordDetailed(
            context = context,
            pdfFile = reProtectedFile,
            currentPassword = secondPass,
            outputName = "secret_unlocked.pdf",
            inPlace = false
        )
        assertTrue(unprotect is PdfEngine.UnprotectResult.Success)
        val unlockedFile = (unprotect as PdfEngine.UnprotectResult.Success).file
        PDDocument.load(unlockedFile).use { doc ->
            assertFalse(doc.isEncrypted)
            assertEquals(2, doc.numberOfPages)
        }
    }

    // =========================================================================
    // TASK 2 TESTS — Real Offline Office Conversions (.docx, .xlsx, .pptx)
    // =========================================================================

    @Test
    fun `convertOfficeToPdfDetailed converts real DOCX with headings, paragraphs, lists, tables, and images to PDF`() = runBlocking {
        val docxFile = File(context.cacheDir, "quarterly_plan.docx")
        val pngBytes = createSamplePngBytes()

        ZipOutputStream(FileOutputStream(docxFile)).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>""".trimIndent().toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rIdImg1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/chart.png"/>
                </Relationships>""".trimIndent().toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/media/chart.png"))
            zos.write(pngBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                    <w:body>
                        <w:p>
                            <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                            <w:r><w:t>Executive Summary 2026</w:t></w:r>
                        </w:p>
                        <w:p>
                            <w:r><w:rPr><w:b/></w:rPr><w:t>Key Objective: </w:t></w:r>
                            <w:r><w:t>Deliver offline document processing on Android.</w:t></w:r>
                        </w:p>
                        <w:p>
                            <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
                            <w:r><w:t>Milestone Alpha Completed</w:t></w:r>
                        </w:p>
                        <w:tbl>
                            <w:tr>
                                <w:tc><w:p><w:r><w:t>Department</w:t></w:r></w:p></w:tc>
                                <w:tc><w:p><w:r><w:t>Budget Allocation</w:t></w:r></w:p></w:tc>
                            </w:tr>
                            <w:tr>
                                <w:tc><w:p><w:r><w:t>Engineering R&amp;D</w:t></w:r></w:p></w:tc>
                                <w:tc><w:p><w:r><w:t>$450,000</w:t></w:r></w:p></w:tc>
                            </w:tr>
                        </w:tbl>
                        <w:p>
                            <w:r>
                                <w:drawing>
                                    <a:graphic>
                                        <a:graphicData>
                                            <a:blip r:embed="rIdImg1"/>
                                        </a:graphicData>
                                    </a:graphic>
                                </w:drawing>
                            </w:r>
                        </w:p>
                    </w:body>
                </w:document>""".trimIndent().toByteArray()
            )
            zos.closeEntry()
        }

        val result = PdfEngine.convertOfficeToPdfDetailed(
            context = context,
            uri = Uri.fromFile(docxFile),
            expectedType = OfficeToPdfConverter.OfficeDocumentType.WORD_DOCX,
            outputName = "quarterly_plan.pdf"
        )

        assertTrue("Expected DOCX conversion success but got $result", result is OfficeToPdfConverter.OfficeConversionResult.Success)
        val success = result as OfficeToPdfConverter.OfficeConversionResult.Success
        assertTrue(success.file.exists())
        assertTrue(success.pageCount >= 1)

        PDDocument.load(success.file).use { doc ->
            val extracted = PDFTextStripper().getText(doc)
            assertTrue(extracted.contains("Executive Summary 2026"))
            assertTrue(extracted.contains("Deliver offline document processing on Android."))
            assertTrue(extracted.contains("Milestone Alpha Completed"))
            assertTrue(extracted.contains("Engineering R&D"))
            assertTrue(extracted.contains("$450,000"))
        }
    }

    @Test
    fun `convertOfficeToPdfDetailed converts real XLSX with multiple sheets, shared strings, and sparse cells to PDF`() = runBlocking {
        val xlsxFile = File(context.cacheDir, "financial_ledger.xlsx")

        ZipOutputStream(FileOutputStream(xlsxFile)).use { zos ->
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets>
                        <sheet name="Revenue Q1" sheetId="1" r:id="rId1"/>
                        <sheet name="Expenses Q1" sheetId="2" r:id="rId2"/>
                    </sheets>
                </workbook>""".trimIndent().toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                </Relationships>""".trimIndent().toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="4" uniqueCount="4">
                    <si><t>Product Line</t></si>
                    <si><t>Units Sold</t></si>
                    <si><t>Enterprise Suite</t></si>
                    <si><t>Cloud Infrastructure</t></si>
                </sst>""".trimIndent().toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <sheetData>
                        <row r="1">
                            <c r="A1" t="s"><v>0</v></c>
                            <c r="B1" t="s"><v>1</v></c>
                        </row>
                        <row r="2">
                            <c r="A2" t="s"><v>2</v></c>
                            <c r="B2"><v>1280</v></c>
                        </row>
                    </sheetData>
                </worksheet>""".trimIndent().toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <sheetData>
                        <row r="1">
                            <c r="A1" t="s"><v>3</v></c>
                            <c r="C1"><f>SUM(100,250)</f><v>350.75</v></c>
                        </row>
                    </sheetData>
                </worksheet>""".trimIndent().toByteArray()
            )
            zos.closeEntry()
        }

        val result = PdfEngine.convertOfficeToPdfDetailed(
            context = context,
            uri = Uri.fromFile(xlsxFile),
            expectedType = OfficeToPdfConverter.OfficeDocumentType.EXCEL_XLSX,
            outputName = "financial_ledger.pdf"
        )

        assertTrue("Expected XLSX conversion success but got $result", result is OfficeToPdfConverter.OfficeConversionResult.Success)
        val success = result as OfficeToPdfConverter.OfficeConversionResult.Success
        assertTrue(success.file.exists())
        assertTrue(success.pageCount >= 2)

        PDDocument.load(success.file).use { doc ->
            val extracted = PDFTextStripper().getText(doc)
            assertTrue(extracted.contains("Revenue Q1"))
            assertTrue(extracted.contains("Enterprise Suite"))
            assertTrue(extracted.contains("1280"))
            assertTrue(extracted.contains("Expenses Q1"))
            assertTrue(extracted.contains("Cloud Infrastructure"))
            assertTrue(extracted.contains("350.75"))
        }
    }

    @Test
    fun `convertOfficeToPdfDetailed converts real PPTX presentation slides with text, tables, and images to PDF`() = runBlocking {
        val pptxFile = File(context.cacheDir, "product_keynote.pptx")
        val pngBytes = createSamplePngBytes()

        ZipOutputStream(FileOutputStream(pptxFile)).use { zos ->
            zos.putNextEntry(ZipEntry("ppt/presentation.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <p:sldIdLst>
                        <p:sldId id="256" r:id="rIdSlide1"/>
                        <p:sldId id="257" r:id="rIdSlide2"/>
                    </p:sldIdLst>
                </p:presentation>""".trimIndent().toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("ppt/_rels/presentation.xml.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rIdSlide1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
                    <Relationship Id="rIdSlide2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
                </Relationships>""".trimIndent().toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("ppt/slides/_rels/slide1.xml.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rIdPic1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/slide_banner.png"/>
                </Relationships>""".trimIndent().toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("ppt/media/slide_banner.png"))
            zos.write(pngBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("ppt/slides/slide1.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                       xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                       xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <p:cSld>
                        <p:spTree>
                            <p:sp>
                                <p:nvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                                <p:txBody>
                                    <a:p><a:r><a:t>Next-Gen Architecture Overview</a:t></a:r></a:p>
                                </p:txBody>
                            </p:sp>
                            <p:sp>
                                <p:nvSpPr><p:nvPr><p:ph type="body"/></p:nvPr></p:nvSpPr>
                                <p:txBody>
                                    <a:p><a:r><a:t>Zero cloud dependencies required</a:t></a:r></a:p>
                                </p:txBody>
                            </p:sp>
                            <p:pic>
                                <p:blipFill><a:blip r:embed="rIdPic1"/></p:blipFill>
                            </p:pic>
                        </p:spTree>
                    </p:cSld>
                </p:sld>""".trimIndent().toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("ppt/slides/slide2.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                       xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                    <p:cSld>
                        <p:spTree>
                            <p:sp>
                                <p:nvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                                <p:txBody>
                                    <a:p><a:r><a:t>Performance Benchmarks</a:t></a:r></a:p>
                                </p:txBody>
                            </p:sp>
                            <p:graphicFrame>
                                <a:graphic>
                                    <a:graphicData>
                                        <a:tbl>
                                            <a:tr>
                                                <a:tc><a:txBody><a:p><a:r><a:t>Metric</a:t></a:r></a:p></a:txBody></a:tc>
                                                <a:tc><a:txBody><a:p><a:r><a:t>Latency</a:t></a:r></a:p></a:txBody></a:tc>
                                            </a:tr>
                                            <a:tr>
                                                <a:tc><a:txBody><a:p><a:r><a:t>Cold Start</a:t></a:r></a:p></a:txBody></a:tc>
                                                <a:tc><a:txBody><a:p><a:r><a:t>42 ms</a:t></a:r></a:p></a:txBody></a:tc>
                                            </a:tr>
                                        </a:tbl>
                                    </a:graphicData>
                                </a:graphic>
                            </p:graphicFrame>
                        </p:spTree>
                    </p:cSld>
                </p:sld>""".trimIndent().toByteArray()
            )
            zos.closeEntry()
        }

        val result = PdfEngine.convertOfficeToPdfDetailed(
            context = context,
            uri = Uri.fromFile(pptxFile),
            expectedType = OfficeToPdfConverter.OfficeDocumentType.POWERPOINT_PPTX,
            outputName = "product_keynote.pdf"
        )

        assertTrue("Expected PPTX conversion success but got $result", result is OfficeToPdfConverter.OfficeConversionResult.Success)
        val success = result as OfficeToPdfConverter.OfficeConversionResult.Success
        assertEquals(2, success.pageCount)

        PDDocument.load(success.file).use { doc ->
            assertEquals(2, doc.numberOfPages)
            val extracted = PDFTextStripper().getText(doc)
            assertTrue(extracted.contains("Next-Gen Architecture Overview"))
            assertTrue(extracted.contains("Zero cloud dependencies required"))
            assertTrue(extracted.contains("Performance Benchmarks"))
            assertTrue(extracted.contains("Cold Start"))
            assertTrue(extracted.contains("42 ms"))
        }
    }

    @Test
    fun `convertOfficeToPdfDetailed detects legacy OLE2 binary files and corrupted archives cleanly`() = runBlocking {
        // Legacy OLE2 signature (D0 CF 11 E0 A1 B1 1A E1)
        val legacyDoc = File(context.cacheDir, "legacy.doc")
        legacyDoc.writeBytes(
            byteArrayOf(
                0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
                0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
                0x00, 0x01, 0x02, 0x03
            )
        )

        val legacyResult = PdfEngine.convertOfficeToPdfDetailed(
            context = context,
            uri = Uri.fromFile(legacyDoc),
            expectedType = OfficeToPdfConverter.OfficeDocumentType.WORD_DOCX,
            outputName = "legacy_out.pdf"
        )
        assertTrue(legacyResult is OfficeToPdfConverter.OfficeConversionResult.Error)
        assertEquals(
            OfficeToPdfConverter.OfficeConversionError.LEGACY_BINARY_FORMAT_UNSUPPORTED,
            (legacyResult as OfficeToPdfConverter.OfficeConversionResult.Error).error
        )

        // Random corrupted non-ZIP bytes
        val corruptFile = File(context.cacheDir, "corrupt.docx")
        corruptFile.writeBytes("Not a valid zip archive at all".toByteArray())

        val corruptResult = PdfEngine.convertOfficeToPdfDetailed(
            context = context,
            uri = Uri.fromFile(corruptFile),
            expectedType = OfficeToPdfConverter.OfficeDocumentType.WORD_DOCX,
            outputName = "corrupt_out.pdf"
        )
        assertTrue(corruptResult is OfficeToPdfConverter.OfficeConversionResult.Error)
        assertEquals(
            OfficeToPdfConverter.OfficeConversionError.INVALID_OR_CORRUPTED_PACKAGE,
            (corruptResult as OfficeToPdfConverter.OfficeConversionResult.Error).error
        )
    }
}
