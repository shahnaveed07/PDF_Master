package com.dsc.pdfmaster

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.dsc.pdfmaster.di.AppModule
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.screens.HomeScreen
import com.dsc.pdfmaster.ui.screens.MergePdfScreen
import com.dsc.pdfmaster.ui.theme.PdfMasterTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(application)
        AppModule.initialize(application)
    }

    @Test
    fun greeting_screenshot() {
        composeTestRule.setContent {
            PdfMasterTheme {
                Text("PDF Master")
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }

    @Test
    fun home_screen_phone_screenshot() {
        val vm = PdfMasterViewModel(application)
        composeTestRule.setContent {
            PdfMasterTheme(darkTheme = true) {
                HomeScreen(
                    viewModel = vm,
                    onOpenPdfViewer = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/home_screen_phone.png")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun home_screen_tablet_screenshot() {
        val vm = PdfMasterViewModel(application)
        composeTestRule.setContent {
            PdfMasterTheme(darkTheme = true) {
                HomeScreen(
                    viewModel = vm,
                    onOpenPdfViewer = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/home_screen_tablet.png")
    }

    @Test
    fun merge_screen_phone_screenshot() {
        val vm = PdfMasterViewModel(application)
        composeTestRule.setContent {
            PdfMasterTheme(darkTheme = true) {
                MergePdfScreen(
                    viewModel = vm,
                    onBack = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/merge_screen_phone.png")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun merge_screen_tablet_screenshot() {
        val vm = PdfMasterViewModel(application)
        composeTestRule.setContent {
            PdfMasterTheme(darkTheme = true) {
                MergePdfScreen(
                    viewModel = vm,
                    onBack = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/merge_screen_tablet.png")
    }
}
