package com.dsc.pdfmaster

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute
import com.dsc.pdfmaster.ui.adaptive.WindowSizeClass
import com.dsc.pdfmaster.ui.screens.ConvertPdfScreen
import com.dsc.pdfmaster.ui.screens.HomeScreen
import com.dsc.pdfmaster.ui.screens.RecentFilesScreen
import com.dsc.pdfmaster.ui.theme.PdfMasterTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecentFilesAndOfficePdfRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var viewModel: PdfMasterViewModel
    private lateinit var repository: PdfRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = PdfMasterViewModel(app)
        repository = viewModel.repository
        viewModel.resetProcessingState()
        viewModel.clearConversionState()
        viewModel.setSearchQuery("")
        runBlocking {
            repository.clearHistory()
        }
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    private fun createValidPdfFile(name: String): File {
        val dir = File(app.filesDir, "test_pdfs").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF".toByteArray())
        return file
    }

    @Test
    fun test_recentFilesScreen_deduplicatesPinnedAndUnpinnedEntries() {
        val filePinned = createValidPdfFile("pinned_doc.pdf")
        val fileFav = createValidPdfFile("favorite_doc.pdf")
        val fileRegular = createValidPdfFile("regular_doc.pdf")

        runBlocking {
            repository.registerPdfFile(filePinned, knownPageCount = 1)
            repository.updatePinned(filePinned.absolutePath, true)
            repository.registerPdfFile(fileFav, knownPageCount = 1)
            repository.updateFavorite(fileFav.absolutePath, true)
            repository.registerPdfFile(fileRegular, knownPageCount = 1)
        }

        composeTestRule.setContent {
            PdfMasterTheme {
                RecentFilesScreen(
                    viewModel = viewModel,
                    onOpenPdfViewer = {}
                )
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            composeTestRule.onAllNodes(hasTestTag("pdf_file_card_${filePinned.absolutePath.hashCode()}"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // In the All Files tab, pinned_doc and favorite_doc must each appear in the top section
        // and must NOT be duplicated in the All Recent Files section!
        composeTestRule.onAllNodes(hasTestTag("pdf_file_card_${filePinned.absolutePath.hashCode()}")).assertCountEquals(1)
        composeTestRule.onAllNodes(hasTestTag("pdf_file_card_${fileFav.absolutePath.hashCode()}")).assertCountEquals(1)

        // Scroll to the unpinned recent files section and verify regular_doc appears once
        composeTestRule.onNodeWithTag("recent_files_lazy_column")
            .performScrollToNode(hasTestTag("pdf_file_card_${fileRegular.absolutePath.hashCode()}"))
        composeTestRule.onAllNodes(hasTestTag("pdf_file_card_${fileRegular.absolutePath.hashCode()}")).assertCountEquals(1)
    }

    @Test
    fun test_recentFilesScreen_favoritesTab_displaysOnlyMatchingEntriesOnce() {
        val filePinned = createValidPdfFile("pinned_item.pdf")
        val fileFav = createValidPdfFile("fav_item.pdf")
        val fileRegular = createValidPdfFile("regular_item.pdf")

        runBlocking {
            repository.registerPdfFile(filePinned, knownPageCount = 1)
            repository.updatePinned(filePinned.absolutePath, true)
            repository.registerPdfFile(fileFav, knownPageCount = 1)
            repository.updateFavorite(fileFav.absolutePath, true)
            repository.registerPdfFile(fileRegular, knownPageCount = 1)
        }

        composeTestRule.setContent {
            PdfMasterTheme {
                RecentFilesScreen(
                    viewModel = viewModel,
                    onOpenPdfViewer = {}
                )
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            composeTestRule.onAllNodes(hasTestTag("pdf_file_card_${filePinned.absolutePath.hashCode()}"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Switch to Favorites & Pinned tab
        composeTestRule.onNodeWithTag("tab_starred_documents").performClick()
        composeTestRule.waitForIdle()

        // Pinned and Fav items appear once
        composeTestRule.onAllNodes(hasTestTag("pdf_file_card_${filePinned.absolutePath.hashCode()}")).assertCountEquals(1)
        composeTestRule.onAllNodes(hasTestTag("pdf_file_card_${fileFav.absolutePath.hashCode()}")).assertCountEquals(1)

        // Regular non-favorite file should NOT appear in this tab
        composeTestRule.onAllNodes(hasTestTag("pdf_file_card_${fileRegular.absolutePath.hashCode()}")).assertCountEquals(0)
    }

    @Test
    fun test_reconcileMissingFiles_retainsExistingFilesAndPrunesMissing(): Unit = runBlocking {
        repository.clearHistory()

        val existingFile = createValidPdfFile("kept_file.pdf")
        val missingPath = File(app.filesDir, "deleted_file.pdf").absolutePath

        repository.registerPdfFile(existingFile)

        // Directly insert an entity pointing to a non-existent file
        val dao = com.dsc.pdfmaster.di.AppModule.providePdfDatabase(app).recentPdfDao()
        dao.insertPdf(
            RecentPdfEntity(
                filePath = missingPath,
                fileName = "deleted_file.pdf",
                fileSizeBytes = 1024L,
                pageCount = 1
            )
        )

        val beforeCount = dao.getAllRecentPdfsSnapshot().size
        assertEquals(2, beforeCount)

        val pruned = repository.reconcileMissingFiles()
        assertEquals(1, pruned)

        val remaining = dao.getAllRecentPdfsSnapshot()
        assertEquals(1, remaining.size)
        assertEquals(existingFile.absolutePath, remaining[0].filePath)
    }

    @Test
    fun test_homeScreen_officeToPdfQuickAction_navigatesToConvertScreen() {
        val expandedWindowSize = WindowSizeClass.calculateFromSize(widthDp = 1000.dp, heightDp = 1000.dp)
        composeTestRule.setContent {
            PdfMasterTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenPdfViewer = {},
                    windowSizeClass = expandedWindowSize
                )
            }
        }

        composeTestRule.onNodeWithTag("home_screen_list")
            .performScrollToNode(hasTestTag("action_office_to_pdf"))
        composeTestRule.onNodeWithTag("action_office_to_pdf").performClick()
        assertEquals(ScreenRoute.CONVERT_PDF, viewModel.currentRoute.value)
    }

    @Test
    fun test_convertPdfScreen_officeCardsVisibleAtTop_andShowsResultCard() {
        composeTestRule.setContent {
            PdfMasterTheme {
                ConvertPdfScreen(
                    viewModel = viewModel,
                    onBack = {}
                )
            }
        }

        // Verify Office to PDF is the top section
        composeTestRule.onNodeWithText("OFFICE TO PDF").assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_card_word_to_pdf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_card_excel_to_pdf").assertExists()
        composeTestRule.onNodeWithTag("option_card_ppt_to_pdf").assertExists()

        // Click Word to PDF
        composeTestRule.onNodeWithTag("option_card_word_to_pdf").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("office_fidelity_disclosure_card").assertExists()

        // Simulate conversion output in ViewModel
        val convertedPdf = createValidPdfFile("Converted_Word.pdf")
        viewModel.setConversionOutputForTesting(listOf(convertedPdf), "1 page converted successfully")
        composeTestRule.waitForIdle()

        // Verify result card and action buttons are displayed
        composeTestRule.onNodeWithTag("conversion_result_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("view_converted_pdf_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_converted_file_button").performScrollTo().assertIsDisplayed()
    }
}
