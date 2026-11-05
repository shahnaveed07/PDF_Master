package com.dsc.pdfmaster

import android.app.Application
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.dsc.pdfmaster.data.database.RecentPdfDao
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.data.model.PdfPageItem
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.PdfPageBitmapCache
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute
import com.dsc.pdfmaster.ui.adaptive.WindowSizeClass
import com.dsc.pdfmaster.ui.screens.HomeScreen
import com.dsc.pdfmaster.ui.screens.MergePdfScreen
import com.dsc.pdfmaster.ui.screens.OnboardingScreen
import com.dsc.pdfmaster.ui.screens.RecentFilesScreen
import com.dsc.pdfmaster.ui.theme.PdfMasterTheme
import com.dsc.pdfmaster.ui.viewmodel.MergeViewModel
import com.dsc.pdfmaster.ui.viewmodel.RecentViewModel
import com.dsc.pdfmaster.ui.viewmodel.SecurityViewModel
import com.dsc.pdfmaster.ui.viewmodel.SplitViewModel
import com.dsc.pdfmaster.work.PdfWorkManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/**
 * In-memory Fake implementation of [RecentPdfDao] for fast, deterministic Repository and ViewModel unit tests.
 */
private class FakeRecentPdfDao : RecentPdfDao {
    private val itemsFlow = MutableStateFlow<List<RecentPdfEntity>>(emptyList())

    override fun getAllRecentPdfs(): Flow<List<RecentPdfEntity>> =
        itemsFlow.map { list -> list.sortedByDescending { it.lastAccessedTimestamp } }

    override fun getFavoritePdfs(): Flow<List<RecentPdfEntity>> =
        itemsFlow.map { list ->
            list.filter { it.isFavorite }
                .sortedWith(compareByDescending<RecentPdfEntity> { it.isPinned }.thenByDescending { it.lastAccessedTimestamp })
        }

    override fun getPinnedOrFavoritePdfs(): Flow<List<RecentPdfEntity>> =
        itemsFlow.map { list ->
            list.filter { it.isPinned || it.isFavorite }
                .sortedWith(compareByDescending<RecentPdfEntity> { it.isPinned }.thenByDescending { it.lastAccessedTimestamp })
        }

    override suspend fun getPdfByPath(path: String): RecentPdfEntity? =
        itemsFlow.value.firstOrNull { it.filePath == path }

    override suspend fun insertPdf(pdf: RecentPdfEntity) {
        val current = itemsFlow.value.toMutableList()
        current.removeAll { it.filePath == pdf.filePath }
        current.add(pdf)
        itemsFlow.value = current
    }

    override suspend fun updateFavorite(filePath: String, isFavorite: Boolean) {
        itemsFlow.value = itemsFlow.value.map {
            if (it.filePath == filePath) it.copy(isFavorite = isFavorite) else it
        }
    }

    override suspend fun updatePinned(filePath: String, isPinned: Boolean) {
        itemsFlow.value = itemsFlow.value.map {
            if (it.filePath == filePath) it.copy(isPinned = isPinned) else it
        }
    }

    override suspend fun updateLastReadPage(filePath: String, lastReadPage: Int) {
        itemsFlow.value = itemsFlow.value.map {
            if (it.filePath == filePath) it.copy(lastReadPage = lastReadPage) else it
        }
    }

    override suspend fun getLastReadPage(filePath: String): Int? =
        itemsFlow.value.firstOrNull { it.filePath == filePath }?.lastReadPage

    override suspend fun renamePdf(filePath: String, newName: String) {
        itemsFlow.value = itemsFlow.value.map {
            if (it.filePath == filePath) it.copy(fileName = newName) else it
        }
    }

    override suspend fun deletePdfByPath(filePath: String) {
        itemsFlow.value = itemsFlow.value.filterNot { it.filePath == filePath }
    }

    override suspend fun clearAllRecentPdfs() {
        itemsFlow.value = emptyList()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase4PolishAndCoverageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var fakeDao: FakeRecentPdfDao
    private lateinit var fakeRepository: PdfRepository
    private lateinit var fakeWorkManager: PdfWorkManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(application)
        fakeDao = FakeRecentPdfDao()
        fakeRepository = PdfRepository(application, fakeDao)
        fakeWorkManager = PdfWorkManager(application, fakeRepository)
    }

    private fun createDummyPdf(name: String, pages: Int): File {
        val file = File(application.cacheDir, name)
        PDDocument().use { doc ->
            repeat(pages) {
                doc.addPage(PDPage(PDRectangle.A4))
            }
            doc.save(file)
        }
        return file
    }

    // =========================================================================
    // 1. Repository Unit Tests (with FakeRecentPdfDao)
    // =========================================================================

    @Test
    fun repository_managesRecentFavoritePinnedLastReadPageAndPdfOperations(): Unit = runBlocking {
        val pdf1 = createDummyPdf("repo_doc1.pdf", 2)
        val pdf2 = createDummyPdf("repo_doc2.pdf", 3)

        fakeRepository.registerPdfFile(pdf1, isFavorite = false, knownPageCount = 2)
        fakeRepository.registerPdfFile(pdf2, isFavorite = false, knownPageCount = 3)

        val allPdfs = fakeRepository.allRecentPdfs.first()
        assertEquals(2, allPdfs.size)

        // Favorite & Pin toggles
        fakeRepository.updateFavorite(pdf1.absolutePath, true)
        fakeRepository.updatePinned(pdf2.absolutePath, true)
        fakeRepository.updateLastReadPage(pdf1.absolutePath, 1)

        val pinnedOrFav = fakeRepository.pinnedOrFavoritePdfs.first()
        assertEquals(2, pinnedOrFav.size)
        assertTrue(pinnedOrFav.first().isPinned)
        assertEquals(1, fakeRepository.getLastReadPage(pdf1.absolutePath))

        // Merge via repository
        val merged = fakeRepository.mergePdfs(listOf(pdf1, pdf2), "repo_merged.pdf")
        assertNotNull(merged)
        PDDocument.load(merged!!).use { doc ->
            assertEquals(5, doc.numberOfPages)
        }

        // Split via repository
        val split = fakeRepository.splitPdf(merged, listOf(0, 2, 4), "repo_split.pdf")
        assertNotNull(split)
        PDDocument.load(split!!).use { doc ->
            assertEquals(3, doc.numberOfPages)
        }

        // Organize (rotate + reorder) via repository
        val organized = fakeRepository.organizePages(
            merged,
            listOf(
                PdfPageItem(pageIndex = 1, bitmap = null, rotationDegrees = 90, isSelected = false),
                PdfPageItem(pageIndex = 0, bitmap = null, rotationDegrees = 180, isSelected = false)
            ),
            "repo_organized.pdf"
        )
        assertNotNull(organized)
        PDDocument.load(organized!!).use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertEquals(90, doc.getPage(0).rotation)
            assertEquals(180, doc.getPage(1).rotation)
        }

        pdf1.delete()
        pdf2.delete()
        merged.delete()
        split.delete()
        organized.delete()
    }

    // =========================================================================
    // 2. ViewModel Unit Tests with Fake Dependencies
    // =========================================================================

    @Test
    fun viewModelsWithFakeDependencies_updateUiStateDeterministically(): Unit = runBlocking {
        val mergeVm = MergeViewModel(fakeRepository, fakeWorkManager)
        val splitVm = SplitViewModel(fakeRepository)
        val securityVm = SecurityViewModel(application, fakeRepository)
        val recentVm = RecentViewModel(fakeRepository)

        val fileA = createDummyPdf("vm_a.pdf", 2)
        val fileB = createDummyPdf("vm_b.pdf", 4)

        // MergeViewModel state transitions
        mergeVm.addFiles(listOf(fileA, fileB))
        assertEquals(2, mergeVm.uiState.value.selectedFiles.size)
        mergeVm.removeFile(fileA)
        assertEquals(1, mergeVm.uiState.value.selectedFiles.size)
        mergeVm.clearFiles()
        assertTrue(mergeVm.uiState.value.selectedFiles.isEmpty())

        // SplitViewModel state transitions
        splitVm.setSourceFile(fileA)
        assertEquals(fileA, splitVm.uiState.value.sourceFile)

        // SecurityViewModel key length selection (128-bit vs 256-bit AES)
        assertEquals(256, securityVm.uiState.value.selectedKeyLengthBits)
        securityVm.setKeyLengthBits(128)
        assertEquals(128, securityVm.uiState.value.selectedKeyLengthBits)
        securityVm.setKeyLengthBits(256)
        assertEquals(256, securityVm.uiState.value.selectedKeyLengthBits)

        // RecentViewModel with FakeRecentPdfDao
        fakeRepository.registerPdfFile(fileB, isFavorite = false, knownPageCount = 4)
        fakeRepository.updatePinned(fileB.absolutePath, true)
        recentVm.setSearchQuery("vm_b")
        val pinnedItems = fakeRepository.pinnedOrFavoritePdfs.first()
        assertEquals(1, pinnedItems.size)
        assertTrue(pinnedItems.first().isPinned)

        // PdfMasterViewModel dynamic color & onboarding state
        val mainVm = PdfMasterViewModel(
            application = application,
            repository = fakeRepository,
            workManager = fakeWorkManager,
            bitmapCache = PdfPageBitmapCache()
        )
        mainVm.setDynamicColorEnabled(false)
        assertFalse(mainVm.isDynamicColorEnabled.value)
        mainVm.setDynamicColorEnabled(true)
        assertTrue(mainVm.isDynamicColorEnabled.value)
        mainVm.setDynamicColorEnabled(false)

        mainVm.replayOnboarding()
        assertTrue(mainVm.showOnboarding.value)
        mainVm.completeOnboarding()
        assertFalse(mainVm.showOnboarding.value)
        assertTrue(mainVm.hasCompletedOnboarding.value)

        fileA.delete()
        fileB.delete()
    }

    // =========================================================================
    // 3. Compose UI Tests for Home & Merge Screens + 200% Font Scale + Adaptive
    // =========================================================================

    @Test
    fun composeUi_homeScreen_verifiesHeadingsTouchTargets200PercentFontScaleAndAdaptiveGrid() {
        val mainVm = PdfMasterViewModel(
            application = application,
            repository = fakeRepository,
            workManager = fakeWorkManager,
            bitmapCache = PdfPageBitmapCache()
        )

        val expandedWindowSize = WindowSizeClass.calculateFromSize(widthDp = 900.dp, heightDp = 800.dp)

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            // Render at 200% font scale (2.0f) to verify accessibility & layout resilience
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 2.0f)
            ) {
                PdfMasterTheme(darkTheme = true, dynamicColor = false) {
                    HomeScreen(
                        viewModel = mainVm,
                        onOpenPdfViewer = {},
                        windowSizeClass = expandedWindowSize
                    )
                }
            }
        }

        // Verify heading semantics for TalkBack screen readers
        composeTestRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit)
                .and(hasTestTag("quick_actions_header"))
        ).assertIsDisplayed()

        // Verify Expanded WindowSizeClass renders 4-column tool grid
        composeTestRule.onNodeWithTag("quick_actions_grid_4_cols").assertIsDisplayed()

        // Verify Quick Action cards are clickable and navigate properly
        composeTestRule.onNodeWithTag("action_merge_pdf")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(ScreenRoute.MERGE_PDF, mainVm.currentRoute.value)
    }

    @Test
    fun composeUi_mergeScreen_verifiesEmptyStateFileSelectionAndButtonEnablement() {
        val mainVm = PdfMasterViewModel(
            application = application,
            repository = fakeRepository,
            workManager = fakeWorkManager,
            bitmapCache = PdfPageBitmapCache()
        )
        val file1 = createDummyPdf("ui_merge_1.pdf", 1)
        val file2 = createDummyPdf("ui_merge_2.pdf", 2)

        composeTestRule.setContent {
            PdfMasterTheme(darkTheme = true) {
                MergePdfScreen(
                    viewModel = mainVm,
                    onBack = { mainVm.navigateBack() }
                )
            }
        }

        // Initially empty state is displayed and Merge button is disabled
        composeTestRule.onNodeWithTag("merge_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("execute_merge_button").assertIsNotEnabled()

        // Add 2 PDF files via ViewModel
        mainVm.addFilesForMerge(listOf(file1, file2))
        composeTestRule.waitForIdle()

        // Both items are shown and Merge button becomes enabled
        composeTestRule.onNodeWithText("ui_merge_1.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("ui_merge_2.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("execute_merge_button").assertIsEnabled()

        // Remove one file -> Merge button disables again (< 2 files)
        composeTestRule.onNodeWithTag("remove_merge_item_0").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("execute_merge_button").assertIsNotEnabled()

        file1.delete()
        file2.delete()
    }

    @Test
    fun composeUi_onboardingAndAdaptiveTwoPaneRecentFiles_workCorrectly() {
        val completedState = androidx.compose.runtime.mutableStateOf(false)
        val mainVm = PdfMasterViewModel(
            application = application,
            repository = fakeRepository,
            workManager = fakeWorkManager,
            bitmapCache = PdfPageBitmapCache()
        )
        val tabletSizeClass = WindowSizeClass.calculateFromSize(widthDp = 840.dp, heightDp = 700.dp)

        composeTestRule.setContent {
            PdfMasterTheme {
                if (!completedState.value) {
                    OnboardingScreen(onFinishOnboarding = { completedState.value = true })
                } else {
                    RecentFilesScreen(
                        viewModel = mainVm,
                        onOpenPdfViewer = {},
                        windowSizeClass = tabletSizeClass
                    )
                }
            }
        }

        // Step 1 -> Step 2 -> Step 3 -> Finish
        composeTestRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_next_or_finish_button").performClick()
        composeTestRule.onNodeWithTag("onboarding_next_or_finish_button").performClick()
        composeTestRule.onNodeWithTag("onboarding_next_or_finish_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(completedState.value)

        // Verify Two-Pane Recent Files + Viewer layout is displayed on Tablet WindowSizeClass
        composeTestRule.onNodeWithTag("recent_files_two_pane_layout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recent_files_list_pane").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recent_files_viewer_pane").assertIsDisplayed()
    }

    // =========================================================================
    // 4. Localization & RTL Verification (Hindi, Urdu, Arabic, Spanish, French)
    // =========================================================================

    @Test
    fun localization_providesTranslatedResourcesForHindiUrduArabicSpanishAndFrench() {
        val locales = listOf(
            Locale.forLanguageTag("hi") to "पीडीएफ मर्ज करें",
            Locale.forLanguageTag("ur") to "پی ڈی ایف ضم کریں",
            Locale.forLanguageTag("ar") to "دمج PDF",
            Locale.forLanguageTag("es") to "Unir PDFs",
            Locale.forLanguageTag("fr") to "Fusionner PDF"
        )

        for ((locale, expectedMergeTitle) in locales) {
            val config = Configuration(application.resources.configuration).apply {
                setLocale(locale)
            }
            val localizedContext = application.createConfigurationContext(config)
            val actualMergeTitle = localizedContext.getString(R.string.tool_merge_title)
            assertEquals("Mismatch for locale ${locale.language}", expectedMergeTitle, actualMergeTitle)
        }

        // Verify RTL layout direction for Urdu and Arabic
        for (rtlTag in listOf("ur", "ar")) {
            val rtlConfig = Configuration(application.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(rtlTag))
            }
            assertEquals(
                "Locale $rtlTag should use RTL layout direction",
                android.view.View.LAYOUT_DIRECTION_RTL,
                rtlConfig.layoutDirection
            )
        }
    }
}
