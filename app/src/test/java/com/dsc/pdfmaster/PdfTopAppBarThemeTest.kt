package com.dsc.pdfmaster

import android.app.Application
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.components.PdfTopAppBar
import com.dsc.pdfmaster.ui.theme.MonoDarkOnPrimary
import com.dsc.pdfmaster.ui.theme.MonoDarkPrimary
import com.dsc.pdfmaster.ui.theme.MonoLightOnPrimary
import com.dsc.pdfmaster.ui.theme.MonoLightPrimary
import com.dsc.pdfmaster.ui.theme.PdfMasterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfTopAppBarThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var viewModel: PdfMasterViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = PdfMasterViewModel(app)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    @Test
    fun logoVisibilityAndContrastInDarkMode() {
        var observedColorScheme: ColorScheme? = null

        composeTestRule.setContent {
            PdfMasterTheme(darkTheme = true) {
                observedColorScheme = MaterialTheme.colorScheme
                PdfTopAppBar(
                    title = "PDF Master",
                    searchQuery = "",
                    onSearchQueryChange = {},
                    isDarkMode = true,
                    onToggleDarkMode = {}
                )
            }
        }

        val colorScheme = checkNotNull(observedColorScheme)
        assertEquals("Primary in Dark Mode must be MonoDarkPrimary", MonoDarkPrimary.value, colorScheme.primary.value)
        assertEquals("OnPrimary in Dark Mode must be MonoDarkOnPrimary", MonoDarkOnPrimary.value, colorScheme.onPrimary.value)

        // Logo icon and container are displayed
        composeTestRule.onNodeWithTag("pdf_logo_icon").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pdf_logo_container").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("PDF Master Logo").assertIsDisplayed()

        // Verify contrast between container background (primary) and icon tint (onPrimary)
        val contrastRatio = ColorUtils.calculateContrast(
            colorScheme.onPrimary.toArgb(),
            colorScheme.primary.toArgb()
        )
        // With onPrimary (Black) on primary (White), contrast ratio is 21:1, far exceeding WCAG AAA (7.0)
        assertTrue("Logo contrast in Dark Mode ($contrastRatio) must meet WCAG AAA (>= 7.0)", contrastRatio >= 7.0)
        assertEquals(21.0, contrastRatio, 0.01)

        // Contrast with old hardcoded Color.White would have been 1.0 (invisible)
        val oldHardcodedContrast = ColorUtils.calculateContrast(
            Color.White.toArgb(),
            colorScheme.primary.toArgb()
        )
        assertEquals(1.0, oldHardcodedContrast, 0.01)
    }

    @Test
    fun logoVisibilityAndContrastInLightMode() {
        var observedColorScheme: ColorScheme? = null

        composeTestRule.setContent {
            PdfMasterTheme(darkTheme = false) {
                observedColorScheme = MaterialTheme.colorScheme
                PdfTopAppBar(
                    title = "PDF Master",
                    searchQuery = "",
                    onSearchQueryChange = {},
                    isDarkMode = false,
                    onToggleDarkMode = {}
                )
            }
        }

        val colorScheme = checkNotNull(observedColorScheme)
        assertEquals("Primary in Light Mode must be MonoLightPrimary", MonoLightPrimary.value, colorScheme.primary.value)
        assertEquals("OnPrimary in Light Mode must be MonoLightOnPrimary", MonoLightOnPrimary.value, colorScheme.onPrimary.value)

        // Logo icon and container are displayed
        composeTestRule.onNodeWithTag("pdf_logo_icon").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pdf_logo_container").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("PDF Master Logo").assertIsDisplayed()

        // Verify contrast between container background (primary) and icon tint (onPrimary)
        val contrastRatio = ColorUtils.calculateContrast(
            colorScheme.onPrimary.toArgb(),
            colorScheme.primary.toArgb()
        )
        // With onPrimary (White) on primary (Dark 0xFF121212), contrast ratio is > 18:1, far exceeding WCAG AAA (7.0)
        assertTrue("Logo contrast in Light Mode ($contrastRatio) must meet WCAG AAA (>= 7.0)", contrastRatio >= 7.0)
    }

    @Test
    fun switchingThemesUpdatesLogoContrastImmediatelyWithoutRestart() {
        var isDarkMode by mutableStateOf(true)
        var currentColorScheme: ColorScheme? = null

        composeTestRule.setContent {
            PdfMasterTheme(darkTheme = isDarkMode) {
                currentColorScheme = MaterialTheme.colorScheme
                PdfTopAppBar(
                    title = "PDF Master",
                    searchQuery = "",
                    onSearchQueryChange = { },
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = { isDarkMode = it }
                )
            }
        }

        // 1. Initial State: Dark Mode
        composeTestRule.onNodeWithTag("pdf_logo_icon").assertIsDisplayed()
        var scheme = checkNotNull(currentColorScheme)
        assertEquals(MonoDarkPrimary.value, scheme.primary.value)
        assertEquals(MonoDarkOnPrimary.value, scheme.onPrimary.value)
        var contrast = ColorUtils.calculateContrast(scheme.onPrimary.toArgb(), scheme.primary.toArgb())
        assertTrue(contrast >= 7.0)

        // 2. Click Theme Toggle -> Switches to Light Mode immediately
        composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pdf_logo_icon").assertIsDisplayed()
        scheme = checkNotNull(currentColorScheme)
        assertEquals(MonoLightPrimary.value, scheme.primary.value)
        assertEquals(MonoLightOnPrimary.value, scheme.onPrimary.value)
        contrast = ColorUtils.calculateContrast(scheme.onPrimary.toArgb(), scheme.primary.toArgb())
        assertTrue(contrast >= 7.0)

        // 3. Click Theme Toggle -> Switches back to Dark Mode immediately
        composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pdf_logo_icon").assertIsDisplayed()
        scheme = checkNotNull(currentColorScheme)
        assertEquals(MonoDarkPrimary.value, scheme.primary.value)
        assertEquals(MonoDarkOnPrimary.value, scheme.onPrimary.value)
        contrast = ColorUtils.calculateContrast(scheme.onPrimary.toArgb(), scheme.primary.toArgb())
        assertTrue(contrast >= 7.0)
    }

    @Test
    fun viewModelThemeToggleUpdatesTopAppBarImmediately() {
        viewModel.toggleDarkMode(true)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        var currentColorScheme: ColorScheme? = null

        composeTestRule.setContent {
            val isDark by viewModel.isDarkMode.collectAsState()
            PdfMasterTheme(darkTheme = isDark) {
                currentColorScheme = MaterialTheme.colorScheme
                PdfTopAppBar(
                    title = "PDF Master",
                    searchQuery = "",
                    onSearchQueryChange = {},
                    isDarkMode = isDark,
                    onToggleDarkMode = { viewModel.toggleDarkMode(it) }
                )
            }
        }

        // Initially Dark Mode
        assertEquals(true, viewModel.isDarkMode.value)
        assertEquals(MonoDarkOnPrimary.value, checkNotNull(currentColorScheme).onPrimary.value)

        // Click toggle button
        composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        composeTestRule.waitForIdle()

        // Switched to Light Mode immediately without restart
        assertEquals(false, viewModel.isDarkMode.value)
        assertEquals(MonoLightOnPrimary.value, checkNotNull(currentColorScheme).onPrimary.value)
        composeTestRule.onNodeWithTag("pdf_logo_icon").assertIsDisplayed()
    }
}
